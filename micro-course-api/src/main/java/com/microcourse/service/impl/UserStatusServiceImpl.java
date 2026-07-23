package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.security.UserStatusCheckFilter;
import com.microcourse.util.SecurityUtil;

import com.microcourse.service.OperationLogService;
import com.microcourse.service.UserStatusService;
import com.microcourse.util.IpUtil;
import com.microcourse.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class UserStatusServiceImpl implements UserStatusService {

    private static final Logger log = LoggerFactory.getLogger(UserStatusServiceImpl.class);

    private final UserRepository userRepository;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;
    private final EnrollmentRepository enrollmentRepository;

    public UserStatusServiceImpl(UserRepository userRepository,
                                  RedisUtil redisUtil,
                                  OperationLogService operationLogService,
                                  EnrollmentRepository enrollmentRepository) {
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UserStatusRequest request) {
        UserStatus newStatus;
        try {
            newStatus = UserStatus.fromCode(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "无效的用户状态值: " + request.getStatus());
        }

        User user = userRepository.selectById(id);
        if (user == null) {
            user = userRepository.selectByIdIncludingDeleted(id);
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserStatus currentStatus = UserStatus.fromCode(user.getStatus());
        Integer oldStatus = user.getStatus();
        Integer newStatusCode = request.getStatus();

        if (currentStatus == newStatus) {
            return;
        }

        if (currentStatus == null || !currentStatus.canTransitionTo(newStatus)) {
            log.warn("非法用户状态转换: userId={} {} -> {}", id, currentStatus, newStatus);
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "不允许从 " + currentStatus + " 转换到 " + newStatus);
        }

        if (currentStatus == UserStatus.DELETED && newStatus == UserStatus.ACTIVE) {
            if (user.getDeletedAt() != null) {
                long daysSinceDeleted = ChronoUnit.DAYS.between(user.getDeletedAt(), LocalDateTime.now());
                if (daysSinceDeleted > 180) {
                    throw new BusinessException(ErrorCode.DELETED_USER_RETENTION_EXPIRED);
                }
            }
            // 修复: 恢复操作使用原生 SQL 绕过 @Version 检查
            // raw SQL INSERT 的版本字段可能为 0 或 null，@Version 乐观锁会导致 updateById 返回 0
            int affected = userRepository.restoreToActive(id);
            if (affected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION,
                        "用户状态已被其他操作修改，请刷新后重试");
            }
            writeStatusAuditLog(user, oldStatus, newStatusCode);
            evictUserStatusCache(id);
            log.info("用户状态变更(恢复): userId={} {} -> {}", id, currentStatus, newStatus);
            return;
        }

        // 【P1-C 修复】INACTIVE→ACTIVE 激活守卫
// 设计文档 §1.3 T1: INACTIVE 用户需满足三选一激活条件
// 1. emailVerified=true (邮箱验证链接点击)
// 2. casBound=true (CAS 首次登录绑定)
// 3. adminForceActivate=true (管理员强制激活)
// 【user-domain-drift-fix verify 修复】补充: 4. 操作者是 ADMIN/ACADEMIC 角色
// (用户域权限矩阵: admin/academic 可手动设置用户状态, 视为已认证的管理操作)
if (currentStatus == UserStatus.INACTIVE && newStatus == UserStatus.ACTIVE) {
    Boolean adminForceActivate = request.getAdminForceActivate();
    boolean isPrivileged = SecurityUtil.hasRole("ADMIN")
            || SecurityUtil.hasRole("ACADEMIC");
    boolean verified = Boolean.TRUE.equals(user.getCasBound())
            || Boolean.TRUE.equals(adminForceActivate)
            || isPrivileged;
    if (!verified) {
        log.warn("INACTIVE→ACTIVE 激活守卫阻断: userId={} casBound={} adminForce={} privileged={}",
                id, user.getCasBound(), adminForceActivate, isPrivileged);
        throw new BusinessException(ErrorCode.USER_NOT_ACTIVE_VERIFIED,
                "INACTIVE 用户需邮箱验证或 CAS 绑定后才能激活, 或管理员强制激活");
    }
}

        switch (newStatus) {
            case ACTIVE:
                user.setStatus(UserStatus.ACTIVE.getCode());
                user.setDeletedAt(null);
                /* ---- 【P0-2 补审】ACTIVE 恢复时清除用户 Token 黑名单 ---- */
                /* 【根因】DISABLED→ACTIVE 时，redisUtil.blacklistUserTokens() 设置的
                 *        mc:jwt:user-blacklist:{userId} 未被清除，
                 *        导致恢复后的用户即使拿到新 Token 也无法访问系统（JwtAuthenticationFilter 拦截） */
                try {
                    redisUtil.delete("mc:jwt:user-blacklist:" + id);
                    log.info("用户激活清除 Token 黑名单: userId={}", id);
                } catch (Exception e) {
                    log.warn("清除 Token 黑名单失败 userId={}，不影响主流程", id, e);
                }
                /* ---- 【I-14(跨域)修复】DISABLED→ACTIVE 无 enrollment 恢复 ---- */
                /* 【根因】用户从 DISABLED(2) 恢复为 ACTIVE(1) 时，
                 *        原 cascadeDisableEnrollments() 级联暂停的选课记录(SUSPENDED)
                 *        不会自动恢复为 APPROVED
                 * 【修复】恢复所有 SUSPENDED 状态的 enrollment 为 APPROVED
                 * 【防止再发】所有状态变更的逆向操作必须逆向前置操作的副作用 */
                try {
                    int recovered = enrollmentRepository.update(null,
                            new LambdaUpdateWrapper<Enrollment>()
                                    .eq(Enrollment::getUserId, id)
                                    .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.SUSPENDED.getValue())
                                    .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.APPROVED.getValue())
                                    .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
                    if (recovered > 0) {
                        log.info("用户激活恢复已暂停选课: userId={}, count={}", id, recovered);
                    }
                } catch (Exception e) {
                    log.warn("恢复已暂停选课失败 userId={}，不影响主流程", id, e);
                }
                break;
            case DISABLED:
                user.setStatus(UserStatus.DISABLED.getCode());
                redisUtil.clearLoginFailure(user.getUsername());
                // P1I-001: 禁用用户时批量作废该用户所有活跃 Token（黑名单 TTL = 2h，匹配 accessToken 有效期）
                redisUtil.blacklistUserTokens(id, 7200L);
                // P0-009: 用户禁用后级联暂停所有进行中的选课，从 WAITLIST 移除
                cascadeDisableEnrollments(id);
                break;
            case DELETED:
                user.setStatus(UserStatus.DELETED.getCode());
                user.setDeletedAt(LocalDateTime.now());
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            default:
                user.setStatus(newStatusCode);
                break;
        }

        // P1I-050 修复: 使用 LambdaUpdateWrapper + 显式 version 乐观锁替代 updateById，
        // 确保并发写入时不会丢失更新
        Integer currentVersion = user.getVersion();
        int affected = userRepository.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, id)
                        .eq(User::getVersion, currentVersion)
                        .set(User::getStatus, user.getStatus())
                        .set(User::getDeletedAt, user.getDeletedAt())
                        .set(User::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION,
                    "用户状态已被其他操作修改，请刷新后重试");
        }

        writeStatusAuditLog(user, oldStatus, newStatusCode);
        evictUserStatusCache(id);
        log.info("用户状态变更: userId={} {} -> {}", id, currentStatus, newStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus(status);
        updateStatus(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeacherStatus(Long id, TeacherStatusRequest request) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "只有教师角色才能更新入驻审核状态");
        }

        Integer oldStatus = user.getTeacherStatus();
        Integer newStatus = request.getTeacherStatus();

        /* ---- 【I-15 修复】updateTeacherStatus 增加状态转换白名单 ---- */
        /* 【根因】updateTeacherStatus() 只校验 0 <= newStatus <= 2，不做状态转换规则检查 */
        /*        允许任意状态跳转，如直接从"待审核"跳到"已通过"再跳回"待审核"等非法操作 */
        /* 【修复】增加状态转换白名单，只有合法转换才允许 */
        /* 【防止再发】所有状态变更必须定义合法转换矩阵 */
        java.util.Map<Integer, java.util.Set<Integer>> validTransitions = java.util.Map.of(
            0, java.util.Set.of(1, 2),  // 待审核→通过/驳回
            1, java.util.Set.of(0),      // 已通过→待审核（撤销审核）
            2, java.util.Set.of(0)       // 已驳回→待审核（重新提交）
        );
        java.util.Set<Integer> allowed = validTransitions.get(oldStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法的教师审核状态转换");
        }

        user.setTeacherStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);

        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("TEACHER_STATUS_CHANGE");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        try {
            java.util.Map<String, Object> detailMap = new java.util.LinkedHashMap<>();
            detailMap.put("field", "teacherStatus");
            detailMap.put("old", oldStatus);
            detailMap.put("new", newStatus);
            detailMap.put("reason", request.getReason());
            logEntry.setDetail(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detailMap));
        } catch (Exception e) {
            log.warn("序列化教师状态变更详情失败: {}", e.getMessage());
            logEntry.setDetail("{\"field\":\"teacherStatus\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        }
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);
    }

    /**
     * P0-009: 用户禁用后级联操作 — 暂停进行中的选课，从 WAITLIST 移除
     */
    private void cascadeDisableEnrollments(Long userId) {
        try {
            // 暂停所有 ACTIVE/APPROVED/PENDING 状态的 enrollment
            int suspendedActive = enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<Enrollment>()
                            .eq(Enrollment::getUserId, userId)
                            .in(Enrollment::getEnrollmentStatus,
                                    EnrollmentStatus.APPROVED.getValue(),
                                    EnrollmentStatus.PENDING.getValue())
                            .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.SUSPENDED.getValue())
                            .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
            log.info("[P0-009] 用户禁用级联暂停 enrollment: userId={}, count={}", userId, suspendedActive);

            // 从 WAITLIST 移除
            int removedWaitlist = enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<Enrollment>()
                            .eq(Enrollment::getUserId, userId)
                            .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                            .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.SUSPENDED.getValue())
                            .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
            if (removedWaitlist > 0) {
                log.info("[P0-009] 用户禁用从 WAITLIST 移除: userId={}, count={}", userId, removedWaitlist);
            }
        } catch (Exception e) {
            log.error("[P0-009] 用户禁用级联 enrollment 失败 userId={}，触发事务回滚", userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "级联禁用选课失败", e);
        }
    }

    private void evictUserStatusCache(Long id) {
        try {
            redisUtil.delete(UserStatusCheckFilter.STATUS_CACHE_PREFIX + id);
        } catch (Exception e) {
            log.warn("清除用户状态缓存失败（不影响状态变更主流程）: userId={}", id, e);
        }
    }

    private void writeStatusAuditLog(User user, Integer oldStatus, Integer newStatus) {
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("STATUS_CHANGE");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        logEntry.setDetail("{\"field\":\"status\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);
    }
}
