package com.microcourse.service;

import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
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
}