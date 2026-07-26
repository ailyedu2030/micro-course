package com.microcourse.service;

import com.microcourse.dto.LoginResponse;

/**
 * CAS 登录事务服务。
 */
public interface AuthCasLoginService {

    /**
     * 根据 CAS 用户名完成本地登录或注册。
     *
     * @param casUsername CAS 用户名
     * @return 登录响应
     */
    LoginResponse loginOrRegister(String casUsername);
}
