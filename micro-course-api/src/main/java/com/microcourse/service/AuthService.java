package com.microcourse.service;

import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.UserVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前登录用户信息
     */
    UserVO getCurrentUser();

    /**
     * 刷新令牌
     * @param refreshToken 刷新令牌
     * @return 新的登录响应
     */
    LoginResponse refresh(String refreshToken);

    /**
     * 用户登出
     */
    void logout();

    /**
     * CAS单点登录
     * @param ticket CAS票据
     * @param state 状态参数
     * @return 登录响应
     */
    LoginResponse casLogin(String ticket, String state);
}