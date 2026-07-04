package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.AddStudentRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.TeachingClassCreateRequest;
import com.microcourse.dto.TeachingClassStudentVO;
import com.microcourse.dto.TeachingClassUpdateRequest;
import com.microcourse.dto.TeachingClassVO;
import com.microcourse.dto.UpdateStudentStatusRequest;
import com.microcourse.service.TeachingClassService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teaching-classes")
public class TeachingClassController {

    private final TeachingClassService teachingClassService;

    public TeachingClassController(TeachingClassService teachingClassService) {
        this.teachingClassService = teachingClassService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<TeachingClassVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Integer status) {
        PageResult<TeachingClassVO> result = teachingClassService.page(page, size, teacherId, courseId, semester, status);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<TeachingClassVO> getById(@PathVariable Long id) {
        TeachingClassVO vo = teachingClassService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<TeachingClassVO> create(@Valid @RequestBody TeachingClassCreateRequest request) {
        TeachingClassVO vo = teachingClassService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<TeachingClassVO> update(@PathVariable Long id,
                                     @Valid @RequestBody TeachingClassUpdateRequest request) {
        TeachingClassVO vo = teachingClassService.update(id, request);
        return R.ok(vo);
    }

    /**
     * DELETE /api/teaching-classes/{id}
     * 删除教学班
     * 权限：ADMIN（依据 权限矩阵 v2.0 §2.9 DELETE_TEACHING_CLASS = 仅 ADMIN）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        teachingClassService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'ACADEMIC')")
    public R<List<TeachingClassStudentVO>> getClassStudents(@PathVariable Long id) {
        List<TeachingClassStudentVO> list = teachingClassService.getClassStudents(id);
        return R.ok(list);
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> addStudent(@PathVariable Long id,
                              @Valid @RequestBody AddStudentRequest request) {
        teachingClassService.addStudent(id, request.getUserId());
        return R.ok();
    }

    @DeleteMapping("/{id}/students/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> removeStudent(@PathVariable Long id, @PathVariable Long userId) {
        teachingClassService.removeStudent(id, userId);
        return R.ok();
    }

    @PutMapping("/{id}/students/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> updateStudentStatus(@PathVariable Long id,
                                      @PathVariable Long userId,
                                      @Valid @RequestBody UpdateStudentStatusRequest request) {
        teachingClassService.updateStudentStatus(id, userId, request.getStatus());
        return R.ok();
    }

    /**
     * GET /api/teaching-classes/{id}/schedule
     * 获取教学班课表（Phase A-4 P0-5 新增）
     * 权限：全部已登录角色（STUDENT 已入选 / TEACHER 授课 / ADMIN / ACADEMIC）——
     *      依据 权限矩阵 v2.0 §2.9 READ_TEACHING_CLASS_SCHEDULE。
     * 返回教学班详情（含 schedule / location / semester 字段）。
     */
    @GetMapping("/{id}/schedule")
    @PreAuthorize("isAuthenticated()")
    public R<TeachingClassVO> getSchedule(@PathVariable Long id) {
        return R.ok(teachingClassService.getById(id));
    }

    /**
     * POST /api/teaching-classes/{id}/complete
     * 结课（ACTIVE → COMPLETED）。Round 6 状态机补全。
     * 权限：ADMIN / ACADEMIC / TEACHER（依据 docs/状态机设计.md §4「系统/教师手动结课」）。
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    @AuditedLog("教学班结课")
    public R<Void> complete(@PathVariable Long id) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        teachingClassService.complete(id, currentUserId);
        return R.ok();
    }

    /**
     * POST /api/teaching-classes/{id}/cancel
     * 停开（ACTIVE → CANCELLED），停开原因必填。Round 6 状态机补全。
     * 权限：ADMIN / ACADEMIC（依据 docs/状态机设计.md §4「管理员取消/教师申请停开」）。
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("教学班停开")
    public R<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        String reason = body == null ? null : body.get("reason");
        teachingClassService.cancel(id, reason, currentUserId);
        return R.ok();
    }
}