package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.dto.R;
import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC')")
    // P2-权限对齐：TEACHER 权限受 Service 层数据范围限制（仅能查看自己课程的学生）
    public R<PageResult<UserVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer teacherStatus) {
        UserPageQuery query = new UserPageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setKeyword(keyword);
        if (role != null && !role.isEmpty()) {
            query.setRole(com.microcourse.enums.UserRole.valueOf(role));
        }
        query.setDepartmentId(departmentId);
        query.setStatus(status);
        query.setTeacherStatus(teacherStatus);
        PageResult<UserVO> result = userService.pageUsers(query);
        return R.ok(result);
    }

    /**
     * GET /api/users/{id}
     * 权限矩阵 v4.1: TEACHER 仅可访问自己的 + 自己的学生 (数据范围), 否则 403
     * 当前实现: TEACHER 仅可访问自己 (#id == principal), 数据范围由 Service 层控制
     * (UserServiceImpl.getUserById L93 TEACHER 检查)
     * ADMIN/ACADEMIC 可访问全部
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated() and (#id == authentication.principal or hasRole('ADMIN') or hasRole('ACADEMIC'))")
    public R<UserVO> getById(@PathVariable Long id) {
        UserVO vo = userService.getUserById(id);
        return R.ok(vo);
    }

    /**
     * GET /api/users/{id}/public-profile
     * P0-3 修复：开放教师基本信息接口，返回 realName + avatar + bio，
     * 任何已登录用户（含 STUDENT）均可访问，避免课程详情页 403。
     */
    @GetMapping("/{id}/public-profile")
    @PreAuthorize("isAuthenticated()")
    public R<UserVO> getPublicProfile(@PathVariable Long id) {
        UserVO full = userService.getUserById(id);
        UserVO publicVo = new UserVO();
        publicVo.setId(full.getId());
        publicVo.setRealName(full.getRealName());
        publicVo.setAvatar(full.getAvatar());
        publicVo.setBio(full.getBio());
        publicVo.setRole(full.getRole());
        return R.ok(publicVo);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("创建用户")
    public R<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        UserVO vo = userService.createUser(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
    @AuditedLog("更新用户信息")
    public R<UserVO> update(@PathVariable Long id,
                             @Valid @RequestBody UserUpdateRequest request) {
        UserVO vo = userService.updateUser(id, request);
        return R.ok(vo);
    }

    /**
     * PUT /api/users/{id}/status
     * 修改用户状态 (权限矩阵 v4.1: 仅 ADMIN, 移除 ACADEMIC)
     * 教务处 (ACADEMIC) 不再有用户状态管理权限, 收敛到纯统计/查询
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("修改用户状态")
    public R<Void> updateStatus(@PathVariable Long id,
                                 @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request);
        return R.ok();
    }

    /**
     * PUT /api/users/{id}/teacher-status
     * 更新教师入驻审核状态（ADMIN only）
     * 0=待审核, 1=通过, 2=驳回
     */
    @PutMapping("/{id}/teacher-status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("审核教师入驻状态")
    public R<Void> updateTeacherStatus(@PathVariable Long id,
                                        @Valid @RequestBody TeacherStatusRequest request) {
        userService.updateTeacherStatus(id, request);
        return R.ok();
    }

    /**
     * POST /api/users/batch
     * 批量导入用户（Excel）
     */
    /**
     * POST /api/users/batch
     * 批量导入用户 (Excel)
     * 权限: ADMIN (权限矩阵 v4.1)
     * 【V4 修复】文件校验下沉到 FileUploadUtil, Controller 不再直接读 InputStream
     */
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("批量导入用户")
    public R<BatchImportResultVO> batchImport(@RequestParam("file") MultipartFile file) {
        com.microcourse.util.FileUploadUtil.assertExcel(file);
        BatchImportResultVO result = userService.batchImportUsers(file);
        return R.ok(result);
    }

    /**
     * POST /api/users/{id}/avatar
     * 上传用户头像
     */
    @PostMapping("/{id}/avatar")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
    public R<String> uploadAvatar(@PathVariable Long id,
                                   @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像大小不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (!Arrays.asList("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 JPG/PNG/WebP 格式");
        }
        String avatarUrl = userService.uploadAvatar(id, file);
        return R.ok(avatarUrl);
    }

}