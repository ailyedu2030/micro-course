package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.OfflineSessionCreateRequest;
import com.microcourse.dto.OfflineSessionUpdateRequest;
import com.microcourse.dto.OfflineSessionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.OfflineSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/offline-sessions")
@Validated
@Tag(name = "课程线下课堂管理", description = "线下课堂课程子资源路径（与/api/offline-sessions平行，保留向后兼容）")
public class CourseOfflineSessionController {

    private final OfflineSessionService offlineSessionService;

    public CourseOfflineSessionController(OfflineSessionService offlineSessionService) {
        this.offlineSessionService = offlineSessionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程下所有线下课堂")
    public R<List<OfflineSessionVO>> listByCourse(@PathVariable Long courseId) {
        return R.ok(offlineSessionService.listByCourse(courseId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建线下课堂")
    @Operation(summary = "在课程下创建线下课堂（需指定chapterId）")
    public R<OfflineSessionVO> create(@PathVariable Long courseId,
                                     @RequestParam Long chapterId,
                                     @Valid @RequestBody OfflineSessionCreateRequest request) {
        OfflineSessionVO vo = offlineSessionService.create(chapterId, request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新线下课堂")
    @Operation(summary = "更新线下课堂")
    public R<OfflineSessionVO> update(@PathVariable Long courseId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody OfflineSessionUpdateRequest request) {
        OfflineSessionVO vo = offlineSessionService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除线下课堂")
    @Operation(summary = "删除线下课堂")
    public R<Void> delete(@PathVariable Long courseId, @PathVariable Long id) {
        offlineSessionService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取线下课堂考勤记录")
    public R<PageResult<com.microcourse.dto.AttendanceRecordVO>> getAttendance(
            @PathVariable Long courseId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        return R.ok(offlineSessionService.getAttendance(id, page, size));
    }

    @GetMapping("/attendance-stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程下线下课堂考勤统计")
    public R<java.util.Map<String, Object>> getCourseAttendanceStats(@PathVariable Long courseId) {
        return R.ok(offlineSessionService.getCourseAttendanceStats(courseId));
    }
}
