package com.microcourse.service;

import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.PromoteGradeResultVO;
import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    PageResult<UserVO> pageUsers(UserPageQuery query);

    UserVO getUserById(Long id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(Long id, UserUpdateRequest request);

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

    /**
     * 批量升级学生年级
     * <p>将所有 STUDENT 的 grade +1，并刷新 enrollmentYear/graduationYear 字段。
     * 如果指定了 fromGrade（如 "2024"），只升级当前 grade 等于该值的学生。</p>
     *
     * @param fromGrade 只升级指定年级，为空则升级所有学生
     * @return 升级结果
     */
    PromoteGradeResultVO promoteGrade(String fromGrade);
}