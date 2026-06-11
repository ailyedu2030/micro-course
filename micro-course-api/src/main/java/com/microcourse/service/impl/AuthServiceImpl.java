package com.microcourse.service.impl;

import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = Logger.getLogger(AuthServiceImpl.class.getName());

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository, JwtUtil jwtUtil,
                           BCryptPasswordEncoder passwordEncoder, RedisUtil redisUtil,
                           OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // Step 1: 检查登录失败次数
        int failureCount = redisUtil.getLoginFailureCount(request.getUsername());
        if (failureCount >= 5) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }

        // Step 2: 查询用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    redisUtil.incrLoginFailure(request.getUsername());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        // Step 3: 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            redisUtil.incrLoginFailure(request.getUsername());
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
        redisUtil.clearLoginFailure(request.getUsername());

        // Step 6: 生成 JWT
        String accessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Step 7: 更新 lastLoginAt
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        // Step 8: 记录操作日志
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setAction("LOGIN");
        log.setTargetType("USER");
        log.setTargetId(user.getId());
        log.setIp("0.0.0.0");
        log.setSuccess(true);
        operationLogService.log(log);

        // Step 9: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(7200);
        response.setTokenType("Bearer");
        return response;
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
    public void logout() {
        // Step 1: 从 SecurityContextHolder 获取当前 userId
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            logger.info("Logout: no principal found in security context");
            return;
        }
        Long userId = (Long) principal;
        // Step 2: 获取用户信息以清除登录失败计数
        User user = userRepository.selectById(userId);
        if (user != null) {
            // Step 3: 清除登录失败计数
            redisUtil.clearLoginFailure(user.getUsername());
            logger.info("Logout: cleared login failure count for user " + user.getUsername());

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
        // Step 4: 清除 SecurityContext
        SecurityContextHolder.clearContext();
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