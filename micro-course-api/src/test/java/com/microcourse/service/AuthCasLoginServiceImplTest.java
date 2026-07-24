package com.microcourse.service;

import com.microcourse.dto.LoginResponse;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.AuthCasLoginServiceImpl;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthCasLoginService CAS 事务编排")
class AuthCasLoginServiceImplTest {

    @Test
    @DisplayName("loginOrRegister 对 super admin 用户必须升级为管理员并生成 token")
    void loginOrRegisterUpgradesConfiguredSuperAdmin() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminSettingService adminSettingService = mock(AdminSettingService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        AuthCasLoginServiceImpl service = new AuthCasLoginServiceImpl(
                userRepository, adminSettingService, redisUtil, jwtUtil, operationLogService);

        User user = new User();
        user.setId(9L);
        user.setUsername("cas-admin");
        user.setRole(UserRole.STUDENT);
        user.setDepartmentId(12L);
        user.setStatus(UserStatus.ACTIVE.getCode());
        user.setCasBound(true);

        when(userRepository.findByUsername("cas-admin")).thenReturn(java.util.Optional.of(user));
        when(adminSettingService.getByKey("cas_admin_username")).thenReturn(null);
        when(adminSettingService.getByKey("cas_super_admins")).thenReturn("cas-admin,other");
        when(redisUtil.incrementTokenGeneration(9L)).thenReturn(3L);
        when(jwtUtil.generateToken(9L, "cas-admin", UserRole.ADMIN, 12L)).thenReturn("access");
        when(jwtUtil.generateRefreshToken(9L, 3L)).thenReturn("refresh");

        LoginResponse response = service.loginOrRegister("cas-admin");

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals(UserRole.ADMIN, user.getRole());
        verify(userRepository, times(2)).updateById(user);
        verify(operationLogService).log(any());
    }
}
