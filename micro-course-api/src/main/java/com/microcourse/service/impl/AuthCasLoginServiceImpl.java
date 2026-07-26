package com.microcourse.service.impl;

import com.microcourse.dto.LoginResponse;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.AuthCasLoginService;
import com.microcourse.service.OperationLogService;
import com.microcourse.util.IpUtil;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CAS 登录事务服务实现。
 */
@Service
public class AuthCasLoginServiceImpl implements AuthCasLoginService {

    private static final int TOKEN_EXPIRES_IN_SECONDS = 7200;

    private final UserRepository userRepository;
    private final AdminSettingService adminSettingService;
    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;
    private final OperationLogService operationLogService;

    public AuthCasLoginServiceImpl(UserRepository userRepository,
                                   AdminSettingService adminSettingService,
                                   RedisUtil redisUtil,
                                   JwtUtil jwtUtil,
                                   OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.adminSettingService = adminSettingService;
        this.redisUtil = redisUtil;
        this.jwtUtil = jwtUtil;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginOrRegister(String casUsername) {
        User user = userRepository.findByUsername(casUsername).orElse(null);
        if (user == null) {
            user = new User();
            user.setUsername(casUsername);
            user.setRole(UserRole.STUDENT);
            user.setCasBound(true);
            user.setStatus(UserStatus.ACTIVE.getCode());
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.insert(user);
        } else if (!Boolean.TRUE.equals(user.getCasBound())) {
            user.setCasBound(true);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.updateById(user);
        }

        if (user.getStatus() != null && user.getStatus().intValue() == UserStatus.INACTIVE.getCode()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "账号未激活");
        }
        if (user.getStatus() != null && user.getStatus().intValue() == UserStatus.DISABLED.getCode()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getStatus() != null && user.getStatus().intValue() == UserStatus.DELETED.getCode()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }

        boolean roleUpgraded = false;
        String adminUsername = adminSettingService.getByKey("cas_admin_username");
        if (adminUsername != null && adminUsername.equals(casUsername)) {
            user.setRole(UserRole.ADMIN);
            roleUpgraded = true;
        }
        String superAdmins = adminSettingService.getByKey("cas_super_admins");
        if (superAdmins != null && !superAdmins.isBlank()) {
            for (String superAdmin : superAdmins.split(",")) {
                if (superAdmin.trim().equals(casUsername)) {
                    user.setRole(UserRole.ADMIN);
                    roleUpgraded = true;
                    break;
                }
            }
        }
        if (roleUpgraded) {
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.updateById(user);
        }

        long tokenGeneration = redisUtil.incrementTokenGeneration(user.getId());
        if (tokenGeneration == 0) {
            tokenGeneration = 1;
        }
        String accessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), tokenGeneration);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("CAS_LOGIN");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        logEntry.setDetail("{\"method\":\"AuthController.casLogin\",\"path\":\"POST /api/auth/cas/login\",\"status\":200}");
        logEntry.setDurationMs(0);
        operationLogService.log(logEntry);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(TOKEN_EXPIRES_IN_SECONDS);
        response.setTokenType("Bearer");
        return response;
    }
}
