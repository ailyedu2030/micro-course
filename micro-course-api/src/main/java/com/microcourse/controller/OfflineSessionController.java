package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.AttendanceRecordVO;
import com.microcourse.dto.AttendanceUpdateRequest;
import com.microcourse.dto.OfflineSessionCreateRequest;
import com.microcourse.dto.OfflineSessionUpdateRequest;
import com.microcourse.dto.OfflineSessionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.OfflineSessionService;
import com.microcourse.util.SecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class OfflineSessionController {

    private final OfflineSessionService offlineSessionService;

    public OfflineSessionController(OfflineSessionService offlineSessionService) {
        this.offlineSessionService = offlineSessionService;
    }

    @GetMapping("/chapters/{chapterId}/offline-sessions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<PageResult<OfflineSessionVO>> pageByChapter(
            @PathVariable Long chapterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<OfflineSessionVO> result = offlineSessionService.pageByChapter(chapterId, page, size);
        return R.ok(result);
    }

    @PostMapping("/chapters/{chapterId}/offline-sessions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建线下活动")
    public R<OfflineSessionVO> create(
            @PathVariable Long chapterId,
            @Valid @RequestBody OfflineSessionCreateRequest request) {
        OfflineSessionVO vo = offlineSessionService.create(chapterId, request);
        return R.ok(vo);
    }

    @PutMapping("/offline-sessions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新线下活动")
    public R<OfflineSessionVO> update(
            @PathVariable Long id,
            @Valid @RequestBody OfflineSessionUpdateRequest request) {
        OfflineSessionVO vo = offlineSessionService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/offline-sessions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除线下活动")
    public R<Void> delete(@PathVariable Long id) {
        offlineSessionService.delete(id);
        return R.ok();
    }

    @GetMapping("/offline-sessions/{id}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<PageResult<AttendanceRecordVO>> getAttendance(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<AttendanceRecordVO> result = offlineSessionService.getAttendance(id, page, size);
        return R.ok(result);
    }

    @PutMapping("/offline-sessions/{id}/attendance/{recordId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新签到状态")
    public R<Void> updateAttendance(
            @PathVariable Long id,
            @PathVariable Long recordId,
            @Valid @RequestBody AttendanceUpdateRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        offlineSessionService.updateAttendance(recordId, request.getStatus(), operatorId);
        return R.ok();
    }

    @PostMapping("/offline-sessions/{id}/checkin")
    @PreAuthorize("hasRole('STUDENT')")
    @AuditedLog("学生签到")
    public R<Void> checkin(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        offlineSessionService.checkin(id, userId);
        return R.ok();
    }

    @GetMapping("/chapters/{chapterId}/my-attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<AttendanceRecordVO>> getMyAttendance(@PathVariable Long chapterId) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<AttendanceRecordVO> list = offlineSessionService.getMyAttendance(chapterId, userId);
        return R.ok(list);
    }
}
