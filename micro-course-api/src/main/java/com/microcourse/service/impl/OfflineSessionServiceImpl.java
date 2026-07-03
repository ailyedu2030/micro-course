package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.AttendanceRecordVO;
import com.microcourse.dto.OfflineSessionCreateRequest;
import com.microcourse.dto.OfflineSessionUpdateRequest;
import com.microcourse.dto.OfflineSessionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.AttendanceRecord;
import com.microcourse.entity.ChapterOfflineSession;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.AttendanceRecordRepository;
import com.microcourse.repository.ChapterOfflineSessionRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.OfflineSessionService;
import com.microcourse.util.SecurityUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OfflineSessionServiceImpl implements OfflineSessionService {

    private final ChapterOfflineSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    public OfflineSessionServiceImpl(ChapterOfflineSessionRepository sessionRepository,
                                      AttendanceRecordRepository attendanceRepository,
                                      CourseChapterRepository chapterRepository,
                                      CourseRepository courseRepository,
                                      EnrollmentRepository enrollmentRepository,
                                      UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OfflineSessionVO> pageByChapter(Long chapterId, int page, int size) {
        LambdaQueryWrapper<ChapterOfflineSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterOfflineSession::getChapterId, chapterId);
        wrapper.orderByAsc(ChapterOfflineSession::getSortOrder);

        IPage<ChapterOfflineSession> ipage = sessionRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        List<OfflineSessionVO> vos = buildVOsWithAttendance(ipage.getRecords());
        return PageResult.of(vos, ipage.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfflineSessionVO> listByChapter(Long chapterId) {
        LambdaQueryWrapper<ChapterOfflineSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterOfflineSession::getChapterId, chapterId);
        wrapper.orderByAsc(ChapterOfflineSession::getSortOrder);

        List<ChapterOfflineSession> sessions = sessionRepository.selectList(wrapper);
        return buildVOsWithAttendance(sessions);
    }

    @Override
    @Transactional(readOnly = true)
    public OfflineSessionVO getById(Long id) {
        ChapterOfflineSession session = sessionRepository.selectById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "线下活动不存在");
        }
        List<OfflineSessionVO> vos = buildVOsWithAttendance(Collections.singletonList(session));
        return vos.isEmpty() ? convertToVO(session) : vos.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfflineSessionVO create(Long chapterId, OfflineSessionCreateRequest request) {
        CourseChapter chapter = chapterRepository.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        assertCourseOwnerByCourseId(chapter.getCourseId());

        ChapterOfflineSession session = new ChapterOfflineSession();
        session.setChapterId(chapterId);
        session.setSessionDate(request.getSessionDate());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setLocation(request.getLocation());
        session.setTeacherNotes(request.getTeacherNotes());
        session.setSortOrder(request.getSortOrder());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setVersion(0);

        sessionRepository.insert(session);
        return convertToVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfflineSessionVO update(Long id, OfflineSessionUpdateRequest request) {
        ChapterOfflineSession session = sessionRepository.selectById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "线下活动不存在");
        }

        CourseChapter chapter = chapterRepository.selectById(session.getChapterId());
        if (chapter != null) {
            assertCourseOwnerByCourseId(chapter.getCourseId());
        }

        if (request.getSessionDate() != null) session.setSessionDate(request.getSessionDate());
        if (request.getStartTime() != null) session.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) session.setEndTime(request.getEndTime());
        if (request.getLocation() != null) session.setLocation(request.getLocation());
        if (request.getTeacherNotes() != null) session.setTeacherNotes(request.getTeacherNotes());
        if (request.getSortOrder() != null) session.setSortOrder(request.getSortOrder());
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepository.updateById(session);
        return convertToVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ChapterOfflineSession session = sessionRepository.selectById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "线下活动不存在");
        }

        CourseChapter chapter = chapterRepository.selectById(session.getChapterId());
        if (chapter != null) {
            assertCourseOwnerByCourseId(chapter.getCourseId());
        }

        sessionRepository.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long sessionId, Long userId) {
        ChapterOfflineSession session = sessionRepository.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "签到活动不存在");
        }

        LocalDate today = LocalDate.now();
        if (!session.getSessionDate().equals(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "签到时间不在活动日期范围内");
        }

        LocalTime now = LocalTime.now();
        LocalTime windowStart = session.getStartTime().minusMinutes(15);
        LocalTime windowEnd = session.getStartTime().plusMinutes(30);
        if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "不在签到时间窗口内（课前15分钟至课后30分钟）");
        }

        CourseChapter chapter = chapterRepository.selectById(session.getChapterId());
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }

        long enrollmentCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, chapter.getCourseId())
                        .eq(Enrollment::getUserId, userId)
                        .in(Enrollment::getEnrollmentStatus, "ENROLLED", "IN_PROGRESS", "COMPLETED"));
        if (enrollmentCount == 0) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED);
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setStatus("PRESENT");
        record.setCheckinTime(LocalDateTime.now());

        try {
            attendanceRepository.insert(record);
        } catch (DataIntegrityViolationException e) {
            // Idempotent: unique constraint (session_id, user_id) prevents duplicate check-in
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AttendanceRecordVO> getAttendance(Long sessionId, int page, int size) {
        ChapterOfflineSession session = sessionRepository.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "线下活动不存在");
        }

        IPage<AttendanceRecord> ipage = attendanceRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getSessionId, sessionId));

        List<AttendanceRecordVO> vos = ipage.getRecords().stream()
                .map(this::convertToAttendanceVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, ipage.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceRecordVO> getMyAttendance(Long chapterId, Long userId) {
        List<ChapterOfflineSession> sessions = sessionRepository.selectList(
                new LambdaQueryWrapper<ChapterOfflineSession>()
                        .eq(ChapterOfflineSession::getChapterId, chapterId));

        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> sessionIds = sessions.stream()
                .map(ChapterOfflineSession::getId)
                .collect(Collectors.toList());

        List<AttendanceRecord> records = attendanceRepository.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .in(AttendanceRecord::getSessionId, sessionIds)
                        .eq(AttendanceRecord::getUserId, userId));

        return records.stream()
                .map(this::convertToAttendanceVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAttendance(Long recordId, String status, Long operatorId) {
        AttendanceRecord record = attendanceRepository.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "签到记录不存在");
        }

        record.setStatus(status);
        record.setUpdatedBy(operatorId);
        attendanceRepository.updateById(record);
    }

    private List<OfflineSessionVO> buildVOsWithAttendance(List<ChapterOfflineSession> sessions) {
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> sessionIds = sessions.stream()
                .map(ChapterOfflineSession::getId)
                .collect(Collectors.toList());

        Map<Long, Map<String, Long>> attendanceCounts = batchCountAttendanceBySession(sessionIds);

        Long chapterId = sessions.get(0).getChapterId();
        Integer totalCount = 0;
        CourseChapter chapter = chapterRepository.selectById(chapterId);
        if (chapter != null) {
            totalCount = Math.toIntExact(enrollmentRepository.selectCount(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, chapter.getCourseId())
                            .in(Enrollment::getEnrollmentStatus, "ENROLLED", "IN_PROGRESS", "COMPLETED")));
        }
        final Integer finalTotalCount = totalCount;

        return sessions.stream().map(session -> {
            OfflineSessionVO vo = convertToVO(session);
            Map<String, Long> counts = attendanceCounts.getOrDefault(session.getId(), Collections.emptyMap());
            vo.setPresentCount(counts.getOrDefault("PRESENT", 0L).intValue());
            vo.setLateCount(counts.getOrDefault("LATE", 0L).intValue());
            vo.setAbsentCount(counts.getOrDefault("ABSENT", 0L).intValue());
            vo.setExcusedCount(counts.getOrDefault("EXCUSED", 0L).intValue());
            vo.setTotalCount(finalTotalCount);
            return vo;
        }).collect(Collectors.toList());
    }

    private Map<Long, Map<String, Long>> batchCountAttendanceBySession(List<Long> sessionIds) {
        List<AttendanceRecord> records = attendanceRepository.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .select(AttendanceRecord::getSessionId, AttendanceRecord::getStatus)
                        .in(AttendanceRecord::getSessionId, sessionIds));

        return records.stream()
                .collect(Collectors.groupingBy(
                        AttendanceRecord::getSessionId,
                        Collectors.groupingBy(AttendanceRecord::getStatus, Collectors.counting())
                ));
    }

    private OfflineSessionVO convertToVO(ChapterOfflineSession session) {
        OfflineSessionVO vo = new OfflineSessionVO();
        vo.setId(session.getId());
        vo.setChapterId(session.getChapterId());
        vo.setSessionDate(session.getSessionDate());
        vo.setStartTime(session.getStartTime());
        vo.setEndTime(session.getEndTime());
        vo.setLocation(session.getLocation());
        vo.setTeacherNotes(session.getTeacherNotes());
        vo.setSortOrder(session.getSortOrder());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        vo.setVersion(session.getVersion());
        return vo;
    }

    private AttendanceRecordVO convertToAttendanceVO(AttendanceRecord record) {
        AttendanceRecordVO vo = new AttendanceRecordVO();
        vo.setId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setUserId(record.getUserId());
        vo.setStatus(record.getStatus());
        vo.setCheckinTime(record.getCheckinTime());

        User user = userRepository.selectById(record.getUserId());
        if (user != null) {
            vo.setStudentName(user.getRealName());
            vo.setStudentNumber(user.getStudentNo());
        }

        return vo;
    }

    private void assertCourseOwnerByCourseId(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }
}
