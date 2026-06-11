package com.microcourse.service.impl;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.entity.OperationLog;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AuthService;
import com.microcourse.service.OperationLogService;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;
    private final HttpServletRequest httpServletRequest;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository, JwtUtil jwtUtil,
                           BCryptPasswordEncoder passwordEncoder, RedisUtil redisUtil,
                           OperationLogService operationLogService,
                           HttpServletRequest httpServletRequest) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            // Step 1: 检查登录失败次数
            int failureCount = 0;
            try {
                failureCount = redisUtil.getLoginFailureCount(request.getUsername());
            } catch (Exception ignored) {
                // Redis 不可用时不做限流
            }
            if (failureCount >= 5) {
                throw new BusinessException(ErrorCode.LOGIN_LOCKED);
            }

            // Step 2: 查询用户
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> {
                        try { redisUtil.incrLoginFailure(request.getUsername()); } catch (Exception ignored) {}
                        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                    });

            // Step 3: 验证密码
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                try { redisUtil.incrLoginFailure(request.getUsername()); } catch (Exception ignored) {}
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            // Step 4: 验证用户状态
            if (user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            if (user.getStatus() == 2) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
            if (user.getStatus() == 3) {
                throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
            }

            // Step 5: 登录成功，清除失败计数
            try { redisUtil.clearLoginFailure(request.getUsername()); } catch (Exception ignored) {}

            // Step 6: 生成 JWT
            String accessToken = jwtUtil.generateToken(
                    user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId());

            // Step 7: 更新 lastLoginAt
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.updateById(user);

            // Step 8: 记录操作日志
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(user.getId());
            logEntry.setAction("LOGIN");
            logEntry.setTargetType("USER");
            logEntry.setTargetId(user.getId());
            logEntry.setIp("0.0.0.0");
            logEntry.setSuccess(true);
            operationLogService.log(logEntry);

            // Step 9: 构建响应
            LoginResponse response = new LoginResponse();
            response.setAccessToken(accessToken);
            response.setRefreshToken(refreshToken);
            response.setExpiresIn(7200);
            response.setTokenType("Bearer");
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Login error for user: " + request.getUsername());
            e.printStackTrace();
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Override
    public UserVO getCurrentUser() {
        // Phase 3.1: 从 SecurityContextHolder 获取当前 userId
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = (Long) principal;
        // Phase 3.2: 查询用户
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return convertToUserVO(user);
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        // Step 1: 验证 refreshToken 有效性
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        // Step 2: 从 refreshToken 提取 userId
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        // Step 3: 查找用户
        User user = userRepository.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // Step 4: 生成新的 accessToken + refreshToken
        String newAccessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        // Step 5: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(7200);
        response.setTokenType("Bearer");
        return response;
    }

    @Override
    @Transactional
    public void logout() {
        // Step 1: 从 SecurityContextHolder 获取当前 userId
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            log.info("Logout: no principal found in security context");
            //仍尝试从 Authorization header 黑名单 token
            blacklistCurrentToken();
            SecurityContextHolder.clearContext();
            return;
        }
        Long userId = (Long) principal;
        // Step 2: 获取用户信息以清除登录失败计数
        User user = userRepository.selectById(userId);
        if (user != null) {
            // Step 3: 清除登录失败计数
            try { redisUtil.clearLoginFailure(user.getUsername()); } catch (Exception ignored) {}
            log.info("Logout: cleared login failure count for user " + user.getUsername());

            // Step 3.5: 记录操作日志
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(user.getId());
            logEntry.setAction("LOGOUT");
            logEntry.setTargetType("USER");
            logEntry.setTargetId(user.getId());
            logEntry.setIp("0.0.0.0");
            logEntry.setSuccess(true);
            operationLogService.log(logEntry);
        }
        // Step 4: 黑名单当前 token
        blacklistCurrentToken();
        // Step 5: 清除 SecurityContext
        SecurityContextHolder.clearContext();
    }

    /**
     * 从当前请求的 Authorization header 提取 token 并加入黑名单
     */
    private void blacklistCurrentToken() {
        String authHeader = httpServletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti = jwtUtil.getJtiFromToken(token);
                long remainingTtl = jwtUtil.getExpirationRemainingSeconds(token);
                if (jti != null && remainingTtl > 0) {
                    redisUtil.blacklistToken(jti, remainingTtl);
                    log.info("Logout: blacklisted token jti=" + jti + " ttl=" + remainingTtl);
                }
            } catch (Exception e) {
                log.warn("Logout: failed to blacklist token: " + e.getMessage());
            }
        }
    }

    @Override
    public LoginResponse casLogin(String ticket, String state) {
        // Phase 3.2 stub 实现
        LoginResponse response = new LoginResponse();
        response.setAccessToken("cas-stub");
        response.setRefreshToken("cas-stub");
        response.setExpiresIn(0);
        response.setTokenType("CAS");
        return response;
    }

    @Override
    @Transactional
    public UserVO updateProfile(UpdateProfileRequest request) {
        Long userId = getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        userRepository.updateById(user);
        return convertToUserVO(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.updateById(user);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return (Long) principal;
    }

    private UserVO convertToUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setMajorId(user.getMajorId());
        vo.setClassId(user.getClassId());
        vo.setGrade(user.getGrade());
        vo.setEnrollmentYear(user.getEnrollmentYear());
        vo.setStudentNo(user.getStudentNo());
        vo.setTeacherNo(user.getTeacherNo());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}