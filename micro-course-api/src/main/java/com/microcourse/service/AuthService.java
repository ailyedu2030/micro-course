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
}