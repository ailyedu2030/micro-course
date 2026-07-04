package com.microcourse.service;

import com.microcourse.dto.AttendanceRecordVO;
import com.microcourse.dto.OfflineSessionCreateRequest;
import com.microcourse.dto.OfflineSessionUpdateRequest;
import com.microcourse.dto.OfflineSessionVO;
import com.microcourse.dto.PageResult;

import java.util.List;

public interface OfflineSessionService {

    PageResult<OfflineSessionVO> pageByChapter(Long chapterId, int page, int size);

    List<OfflineSessionVO> listByChapter(Long chapterId);

    OfflineSessionVO getById(Long id);

    OfflineSessionVO create(Long chapterId, OfflineSessionCreateRequest request);

    OfflineSessionVO update(Long id, OfflineSessionUpdateRequest request);

    void delete(Long id);

    void checkin(Long sessionId, Long userId);

    PageResult<AttendanceRecordVO> getAttendance(Long sessionId, int page, int size);

    List<AttendanceRecordVO> getMyAttendance(Long chapterId, Long userId);

    void updateAttendance(Long recordId, String status, Long operatorId);

    /**
     * O-03: 教师手动签到（代签），不走时间窗口校验。
     *
     * @param sessionId  线下活动ID
     * @param studentId  学生用户ID
     * @param operatorId 操作者ID（TEACHER/ADMIN）
     */
    void manualCheckin(Long sessionId, Long studentId, Long operatorId);
}
