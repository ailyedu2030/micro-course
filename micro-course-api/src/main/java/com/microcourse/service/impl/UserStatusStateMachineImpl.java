package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.User;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.UserStatusStateMachine;
import com.microcourse.service.UserStatusStateMachine.TransitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 用户状态机实现
 *
 * <p>执行流程:
 * <ol>
 *   <li>加载用户</li>
 *   <li>canTransitionTo 白名单检查</li>
 *   <li>注册的守卫 hook (含激活守卫)</li>
 *   <li>乐观锁 CAS UPDATE</li>
 *   <li>副作用 (deletedAt / token 黑名单 / enrollment 恢复)</li>
 * </ol>
 */
@Service
@Primary
public class UserStatusStateMachineImpl implements UserStatusStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(UserStatusStateMachineImpl.class);

    private final UserRepository userRepository;
    /** (from, to) → List<guard> */
    private final Map<UserStatus, Map<UserStatus, List<BiFunction<User, TransitionContext, List<String>>>>> guards
            = new EnumMap<>(UserStatus.class);

    public UserStatusStateMachineImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
        registerInternalGuards();
    }

    /**
     * 内嵌守卫注册 (避免依赖外部 Config bean, 避免双 Bean 歧义)
     */
    private void registerInternalGuards() {
        // INACTIVE → ACTIVE: 激活守卫 (邮箱验证 / CAS 绑定 / 管理员强制激活)
        registerGuard(UserStatus.INACTIVE, UserStatus.ACTIVE, (user, ctx) -> {
            List<String> errors = new ArrayList<>();
            boolean verified = Boolean.TRUE.equals(user.getCasBound())
                    || Boolean.TRUE.equals(ctx.getAdminForceActivate());
            if (!verified) {
                errors.add("INACTIVE 用户需邮箱验证或 CAS 绑定后才能激活, 或管理员强制激活");
            }
            return errors;
        });

        // DELETED → ACTIVE: 180天窗口守卫 (业务层在调用方做, 此处仅占位)
        // 实际 180 天校验在 UserStatusServiceImpl.updateStatus 中保留 (使用原生 SQL 绕过 @Version)

        LOG.info("[UserStatusStateMachine] 守卫注册完成 (1 状态对)");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User transition(Long userId, UserStatus targetStatus, User actor, TransitionContext context) {
        if (context == null) context = TransitionContext.empty();
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "目标状态不能为空");
        }

        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserStatus current = UserStatus.fromCode(user.getStatus());
        if (current == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前状态无效: " + user.getStatus());
        }

        // Step 1: canTransitionTo 白名单
        if (!current.canTransitionTo(targetStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "不允许从 " + current + " 转换到 " + targetStatus);
        }

        // Step 2: 注册的业务守卫
        TransitionGuardResult guardResult = runGuards(current, targetStatus, user, context);
        if (guardResult != TransitionGuardResult.ALLOWED) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE_VERIFIED,
                    "守卫阻断: " + guardResult);
        }

        // Step 3: 乐观锁 CAS 更新
        Integer currentVersion = user.getVersion();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, current.getCode())
                .eq(User::getVersion, currentVersion)
                .set(User::getStatus, targetStatus.getCode())
                .set(User::getUpdatedAt, now)
                .setSql("version = version + 1");

        // 副作用: DELETED 状态写 deletedAt
        if (targetStatus == UserStatus.DELETED) {
            updateWrapper.set(User::getDeletedAt, now);
        }
        // 副作用: 离开 DELETED 状态清 deletedAt
        if (current == UserStatus.DELETED && targetStatus != UserStatus.DELETED) {
            updateWrapper.set(User::getDeletedAt, null);
        }

        int affected = userRepository.update(null, updateWrapper);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION,
                    "用户状态已被其他操作修改");
        }

        LOG.info("[UserStatusStateMachine] userId={} {}→{} actor={}",
                userId, current, targetStatus, actor != null ? actor.getId() : "system");
        user.setStatus(targetStatus.getCode());
        user.setVersion(currentVersion + 1);
        user.setUpdatedAt(now);
        return user;
    }

    @Override
    public TransitionGuardResult checkTransition(Long userId, UserStatus targetStatus,
                                                  User actor, TransitionContext context) {
        if (context == null) context = TransitionContext.empty();
        User user = userRepository.selectById(userId);
        if (user == null) return TransitionGuardResult.COURSE_NOT_FOUND;
        UserStatus current = UserStatus.fromCode(user.getStatus());
        if (current == null) return TransitionGuardResult.INVALID_TRANSITION;

        if (!current.canTransitionTo(targetStatus)) return TransitionGuardResult.INVALID_TRANSITION;

        return runGuards(current, targetStatus, user, context);
    }

    @Override
    public void registerGuard(UserStatus from, UserStatus to,
                              BiFunction<User, TransitionContext, List<String>> guard) {
        guards.computeIfAbsent(from, k -> new EnumMap<>(UserStatus.class))
                .computeIfAbsent(to, k -> new ArrayList<>())
                .add(guard);
    }

    private TransitionGuardResult runGuards(UserStatus from, UserStatus to,
                                            User user, TransitionContext context) {
        Map<UserStatus, List<BiFunction<User, TransitionContext, List<String>>>> toGuards = guards.get(from);
        if (toGuards == null) return TransitionGuardResult.ALLOWED;
        List<BiFunction<User, TransitionContext, List<String>>> guardList = toGuards.get(to);
        if (guardList == null || guardList.isEmpty()) return TransitionGuardResult.ALLOWED;
        for (BiFunction<User, TransitionContext, List<String>> guard : guardList) {
            List<String> errors = guard.apply(user, context);
            if (errors != null && !errors.isEmpty()) {
                LOG.warn("[UserStatusStateMachine] guard blocked userId={} {}→{} errors={}",
                        user.getId(), from, to, errors);
                return TransitionGuardResult.BLOCKED_BY_GUARD;
            }
        }
        return TransitionGuardResult.ALLOWED;
    }
}