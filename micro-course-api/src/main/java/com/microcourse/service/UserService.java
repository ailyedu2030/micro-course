package com.microcourse.service;

import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.StudentSearchVO;
import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.dto.ResetPasswordRequest;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    PageResult<UserVO> pageUsers(UserPageQuery query);

    /**
     * 学生搜索（教师端"教学班添加学生"弹窗使用，仅暴露最小字段）。
     * P1-C 修复：此前教师调用管理端 /api/users 被 403 拦截，添加学生搜索必失败。
     */
    List<StudentSearchVO> searchStudents(String keyword, int size);

    UserVO getUserById(Long id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(Long id, UserUpdateRequest request);

    /**
     * 管理员重置用户密码（A1.7 忘记密码兜底链路）。
     */
    void resetPassword(Long id, ResetPasswordRequest request);

    void updateStatus(Long id, UserStatusRequest request);

    /**
     * 软删除用户（status=3），ADMIN only
     */
    void updateStatus(Long id, Integer status);

    /**
     * 更新教师入驻审核状态
     * @param id 用户ID
     * @param request 审核状态请求
     */
    void updateTeacherStatus(Long id, TeacherStatusRequest request);

    /**
     * 批量导入用户（Excel 解析）
     * @param file Excel 文件
     * @return 导入结果
     */
    BatchImportResultVO batchImportUsers(MultipartFile file);

    /**
     * 上传用户头像，返回头像URL
     */
    String uploadAvatar(Long userId, MultipartFile file);
}
