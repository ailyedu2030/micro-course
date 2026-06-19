package com.microcourse.service;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 更新当前用户资料
     * @param request 更新资料请求
     * @return 更新后的用户信息
     */
    UserVO updateProfile(UpdateProfileRequest request);

    /**
     * 修改当前用户密码
     * @param request 修改密码请求
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * 上传当前用户头像（multipart 文件上传）
     * @param file 头像图片文件
     * @return 头像访问 URL
     */
    String uploadAvatar(MultipartFile file);
}