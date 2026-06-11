package com.microcourse.service.impl;

import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AuthService;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository, JwtUtil jwtUtil,
                           BCryptPasswordEncoder passwordEncoder, RedisUtil redisUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
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

        // Step 8: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(7200);
        response.setTokenType("Bearer");
        return response;
    }

    @Override
    public UserVO getCurrentUser() {
        // Phase 3.1 stub - 从 SecurityContextHolder 获取当前 userId
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // Phase 3.2 完善
        return null;
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