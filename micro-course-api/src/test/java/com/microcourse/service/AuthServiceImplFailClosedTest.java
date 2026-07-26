package com.microcourse.service;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.AuthServiceImpl;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AuthServiceImpl Redis 安全状态 fail-closed")
class AuthServiceImplFailClosedTest {

    private UserRepository userRepository;
    private JwtUtil jwtUtil;
    private BCryptPasswordEncoder passwordEncoder;
    private RedisUtil redisUtil;
    private OperationLogService operationLogService;
    private MockHttpServletRequest request;
    private AdminSettingService adminSettingService;
    private AuthQueryService queryService;
    private CasTicketValidationService casTicketValidationService;
    private AuthCasLoginService authCasLoginService;
    private UserAvatarStorageService userAvatarStorageService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtil = mock(JwtUtil.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        redisUtil = mock(RedisUtil.class);
        operationLogService = mock(OperationLogService.class);
        request = new MockHttpServletRequest();
        adminSettingService = mock(AdminSettingService.class);
        queryService = mock(AuthQueryService.class);
        casTicketValidationService = mock(CasTicketValidationService.class);
        authCasLoginService = mock(AuthCasLoginService.class);
        userAvatarStorageService = mock(UserAvatarStorageService.class);
        authService = new AuthServiceImpl(
                userRepository,
                jwtUtil,
                passwordEncoder,
                redisUtil,
                operationLogService,
                request,
                adminSettingService,
                queryService,
                casTicketValidationService,
                authCasLoginService,
                userAvatarStorageService
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("refresh 轮换旧 refreshToken 失败时必须中断而不是继续签发新 token")
    void refreshShouldFailWhenOldRefreshTokenCannotBeBlacklisted() {
        String refreshToken = "refresh-token";
        User user = activeUser(7L, "alice");

        when(jwtUtil.getJtiUnverified(refreshToken)).thenReturn("pre-jti");
        when(redisUtil.isTokenBlacklisted("pre-jti")).thenReturn(false);
        when(queryService.getRefreshCount("0.0.0.0")).thenReturn(0);
        when(jwtUtil.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(refreshToken)).thenReturn(7L);
        when(jwtUtil.getTokenGeneration(refreshToken)).thenReturn(3L);
        when(redisUtil.getTokenGeneration(7L)).thenReturn(3L);
        when(userRepository.selectById(7L)).thenReturn(user);
        when(jwtUtil.generateToken(7L, "alice", UserRole.STUDENT, 11L)).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(7L, 3L)).thenReturn("new-refresh-token");
        when(jwtUtil.getJtiFromToken(refreshToken)).thenReturn("old-refresh-jti");
        when(jwtUtil.getExpirationRemainingSeconds(refreshToken)).thenReturn(120L);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单服务暂时不可用，请稍后重试"))
                .when(redisUtil).blacklistToken("old-refresh-jti", 120L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh(refreshToken));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("logout 写入黑名单失败时必须中断而不是放过当前 access token")
    void logoutShouldFailWhenCurrentAccessTokenCannotBeBlacklisted() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null)
        );
        request.addHeader("Authorization", "Bearer access-token");
        User user = activeUser(7L, "alice");

        when(userRepository.selectById(7L)).thenReturn(user);
        when(jwtUtil.getJtiFromToken("access-token")).thenReturn("access-jti");
        when(jwtUtil.getExpirationRemainingSeconds("access-token")).thenReturn(60L);
        doNothing().when(operationLogService).log(any());
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单服务暂时不可用，请稍后重试"))
                .when(redisUtil).blacklistToken("access-jti", 60L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.logout());

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("修改密码时若当前 token 无法作废，必须回滚而不是返回成功")
    void changePasswordShouldFailWhenCurrentTokenRevocationIsUnavailable() {
        request.addHeader("Authorization", "Bearer access-token");
        ChangePasswordRequest requestDto = new ChangePasswordRequest();
        requestDto.setOldPassword("oldPassword1");
        requestDto.setNewPassword("newPassword1");
        User user = activeUser(7L, "alice");
        user.setPassword("encoded-old-password");

        when(queryService.getCurrentUserId()).thenReturn(7L);
        when(userRepository.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("oldPassword1", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.matches("newPassword1", "encoded-old-password")).thenReturn(false);
        when(passwordEncoder.encode("newPassword1")).thenReturn("encoded-new-password");
        when(jwtUtil.getJtiFromToken("access-token")).thenReturn("access-jti");
        when(jwtUtil.getExpirationRemainingSeconds("access-token")).thenReturn(60L);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单服务暂时不可用，请稍后重试"))
                .when(redisUtil).blacklistToken("access-jti", 60L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.changePassword(requestDto));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("修改密码时若全用户 token 作废失败，必须中断而不是留下其他设备会话")
    void changePasswordShouldFailWhenBulkTokenRevocationIsUnavailable() {
        request.addHeader("Authorization", "Bearer access-token");
        ChangePasswordRequest requestDto = new ChangePasswordRequest();
        requestDto.setOldPassword("oldPassword1");
        requestDto.setNewPassword("newPassword1");
        User user = activeUser(7L, "alice");
        user.setPassword("encoded-old-password");

        when(queryService.getCurrentUserId()).thenReturn(7L);
        when(userRepository.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("oldPassword1", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.matches("newPassword1", "encoded-old-password")).thenReturn(false);
        when(passwordEncoder.encode("newPassword1")).thenReturn("encoded-new-password");
        when(jwtUtil.getJtiFromToken("access-token")).thenReturn("access-jti");
        when(jwtUtil.getExpirationRemainingSeconds("access-token")).thenReturn(60L);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单服务暂时不可用，请稍后重试"))
                .when(redisUtil).blacklistUserTokens(eq(7L), eq(604800L));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.changePassword(requestDto));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    private static User activeUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(UserRole.STUDENT);
        user.setDepartmentId(11L);
        user.setStatus(UserStatus.ACTIVE.getCode());
        return user;
    }
}
