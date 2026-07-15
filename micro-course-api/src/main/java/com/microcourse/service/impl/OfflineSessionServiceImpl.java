package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.AttendanceRecordVO;
import com.microcourse.dto.OfflineSessionCreateRequest;
import com.microcourse.enums.AttendanceStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.dto.OfflineSessionUpdateRequest;
import com.microcourse.dto.OfflineSessionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.AttendanceRecord;
import com.microcourse.entity.ChapterOfflineSession;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.AttendanceRecordRepository;
import com.microcourse.repository.ChapterOfflineSessionRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.OfflineSessionService;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OfflineSessionServiceImpl implements OfflineSessionService {

    private final ChapterOfflineSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LearningProgressRepository learningProgressRepository;
    private static final Logger log = LoggerFactory.getLogger(OfflineSessionServiceImpl.class);

    private final CourseSectionRepository sectionRepository;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    // P2-009: 考勤统计缓存 TTL（秒）
    private static final long ATTENDANCE_CACHE_TTL = 30;

    /** 签到时间窗口：课前 N 分钟（可通过配置文件调整） */
    @Value("${course.offline.checkin-before-minutes:15}")
    private int checkinBeforeMinutes;
    /** 签到时间窗口：课后 N 分钟（可通过配置文件调整） */
    @Value("${course.offline.checkin-after-minutes:30}")
    private int checkinAfterMinutes;

    public OfflineSessionServiceImpl(ChapterOfflineSessionRepository sessionRepository,
                                      AttendanceRecordRepository attendanceRepository,
                                      CourseChapterRepository chapterRepository,
                                      CourseRepository courseRepository,
                                      EnrollmentRepository enrollmentRepository,
                                      LearningProgressRepository learningProgressRepository,
                                      CourseSectionRepository sectionRepository,
                                      UserRepository userRepository,
                                      RedisUtil redisUtil) {
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.sectionRepository = sectionRepository;
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
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
        long offlineSectionCount = sectionRepository.selectCount(
                new LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getChapterId, chapterId)
                        .eq(CourseSection::getSectionType, "OFFLINE"));
        if (offlineSectionCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该章节没有线下课时，无法创建线下安排");
        }
        assertCourseOwnerByCourseId(chapter.getCourseId());

        if (request.getSessionDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上课日期不能早于今天");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "结束时间必须晚于开始时间");
        }

        ChapterOfflineSession session = new ChapterOfflineSession();
        session.setChapterId(chapterId);
        session.setSessionDate(request.getSessionDate());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setLocation(request.getLocation());
        session.setTeacherNotes(request.getTeacherNotes());

        ChapterOfflineSession lastSession = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChapterOfflineSession>()
                        .eq(ChapterOfflineSession::getChapterId, chapterId)
                        .orderByDesc(ChapterOfflineSession::getSortOrder)
                        .last("LIMIT 1"));
        int nextSort = (lastSession != null && lastSession.getSortOrder() != null)
                ? lastSession.getSortOrder() + 1 : 1;
        session.setSortOrder(nextSort);

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
        LocalTime windowStart = session.getStartTime().minusMinutes(checkinBeforeMinutes);
        LocalTime windowEnd = session.getStartTime().plusMinutes(checkinAfterMinutes);
        if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "不在签到时间窗口内（课前" + checkinBeforeMinutes + "分钟至课后" + checkinAfterMinutes + "分钟）");
        }

        CourseChapter chapter = chapterRepository.selectById(session.getChapterId());
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }

        long enrollmentCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, chapter.getCourseId())
                        .eq(Enrollment::getUserId, userId)
                        .in(Enrollment::getEnrollmentStatus, EnrollmentStatus.LEGACY_ENROLLED_VALUE, EnrollmentStatus.APPROVED.getValue(), EnrollmentStatus.COMPLETED.getValue()));
        if (enrollmentCount == 0) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED);
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setStatus(AttendanceStatus.PRESENT.getValue());
        record.setCheckinTime(LocalDateTime.now());

        try {
            attendanceRepository.insert(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("重复签到(幂等处理): sessionId={}, userId={}", sessionId, userId);
            // Idempotent: unique constraint (session_id, user_id) prevents duplicate check-in
        }

        // P1C-021: 签到成功后更新 learning_progress.offline_attended = true
        if (chapter.getCourseId() != null) {
            LambdaUpdateWrapper<LearningProgress> lpWrapper = new LambdaUpdateWrapper<>();
            lpWrapper.eq(LearningProgress::getUserId, userId)
                    .eq(LearningProgress::getCourseId, chapter.getCourseId())
                    .set(LearningProgress::getOfflineAttended, true)
                    .set(LearningProgress::getUpdatedAt, LocalDateTime.now());
            learningProgressRepository.update(null, lpWrapper);

            // P2-009: 签到后清除考勤缓存
            try {
                redisUtil.delete("offline:attendance:count:" + sessionId);
                redisUtil.delete("offline:attendance:total:" + chapter.getCourseId());
            } catch (Exception e) {
                log.warn("[Attendance] 清除缓存失败 sessionId={}", sessionId, e);
            }
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

        List<AttendanceRecordVO> vos = convertToAttendanceVOs(ipage.getRecords());

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

        return convertToAttendanceVOs(records);
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
        // P2-009: 考勤状态变更后清除该场次的缓存
        try {
            redisUtil.delete("offline:attendance:count:" + record.getSessionId());
        } catch (Exception e) {
            log.warn("[Attendance] 清除缓存失败 sessionId={}", record.getSessionId(), e);
        }
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
            Long courseId = chapter.getCourseId();
            // P2-009: Redis 缓存考勤总人数，30 秒过期
            String cacheKey = "offline:attendance:total:" + courseId;
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof Number n) {
                totalCount = n.intValue();
            } else {
                totalCount = Math.toIntExact(enrollmentRepository.selectCount(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, courseId)
                                .in(Enrollment::getEnrollmentStatus, EnrollmentStatus.LEGACY_ENROLLED_VALUE, EnrollmentStatus.APPROVED.getValue(), EnrollmentStatus.COMPLETED.getValue())));
                redisUtil.set(cacheKey, totalCount, ATTENDANCE_CACHE_TTL, java.util.concurrent.TimeUnit.SECONDS);
            }
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

    /**
     * 批量统计某次线下活动各签到状态的人数。
     * <p>⚠️ P2-9 性能建议：此方法每次查询实时 COUNT，对于高频访问的课程详情页，
     * 建议使用 Redis 缓存（如 key=attendance:count:{sessionId}:{status}）或
     * 在 attendance_records 表增加冗余统计字段，避免每次页面查询都触发全表扫描。</p>
     */
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

    private List<AttendanceRecordVO> convertToAttendanceVOs(List<AttendanceRecord> records) {
        Set<Long> userIds = records.stream()
                .map(AttendanceRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userRepository.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        List<AttendanceRecordVO> result = new ArrayList<>();
        for (AttendanceRecord record : records) {
            AttendanceRecordVO vo = new AttendanceRecordVO();
            vo.setId(record.getId());
            vo.setSessionId(record.getSessionId());
            vo.setUserId(record.getUserId());
            vo.setStatus(record.getStatus());
            vo.setCheckinTime(record.getCheckinTime());

            User user = userMap.get(record.getUserId());
            if (user != null) {
                vo.setStudentName(user.getRealName());
                vo.setStudentNumber(user.getStudentNo());
            }

            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualCheckin(Long sessionId, Long studentId, Long operatorId) {
        ChapterOfflineSession session = sessionRepository.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "线下活动不存在");
        }

        // 校验操作者是 TEACHER/ADMIN 且有课程权限
        CourseChapter chapter = chapterRepository.selectById(session.getChapterId());
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        assertCourseOwnerByCourseId(chapter.getCourseId());

        // 校验学生已选课
        long enrollmentCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, chapter.getCourseId())
                        .eq(Enrollment::getUserId, studentId)
                        .in(Enrollment::getEnrollmentStatus,
                                EnrollmentStatus.LEGACY_ENROLLED_VALUE,
                                EnrollmentStatus.APPROVED.getValue(),
                                EnrollmentStatus.COMPLETED.getValue()));
        if (enrollmentCount == 0) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "该学生未选课，无法签到");
        }

        // 直接插入签到记录（status=PRESENT），不走时间窗口校验
        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setUserId(studentId);
        record.setStatus(AttendanceStatus.PRESENT.getValue());
        record.setCheckinTime(LocalDateTime.now());
        record.setUpdatedBy(operatorId);

        try {
            attendanceRepository.insert(record);
        } catch (DataIntegrityViolationException e) {
            // 幂等：已签到则忽略
            log.info("[manualCheckin] 学生已签到，幂等处理 sessionId={} studentId={}", sessionId, studentId);
        }
        // P2-009: 签到后清除考勤缓存
        try {
            redisUtil.delete("offline:attendance:count:" + sessionId);
            if (chapter.getCourseId() != null) {
                redisUtil.delete("offline:attendance:total:" + chapter.getCourseId());
            }
        } catch (Exception e) {
            log.warn("[Attendance] 清除缓存失败 sessionId={}", sessionId, e);
        }
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN。
     * <p>通用模式：实现逻辑与 ExerciseServiceImpl / VideoServiceImpl / CourseChapterServiceImpl /
     * QuestionServiceImpl 中的同名方法一致。若需统一重构，可抽取到公共工具类。</p>
     */
    private void assertCourseOwnerByCourseId(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    @Override
    public List<OfflineSessionVO> listByCourse(Long courseId) {
        assertCourseOwnerByCourseId(courseId);
        List<Long> chapterIds = chapterRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId)
                        .select(CourseChapter::getId)
        ).stream().map(CourseChapter::getId).collect(java.util.stream.Collectors.toList());

        if (chapterIds.isEmpty()) {
            return java.util.List.of();
        }

        List<ChapterOfflineSession> sessions = sessionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChapterOfflineSession>()
                        .in(ChapterOfflineSession::getChapterId, chapterIds)
                        .orderByDesc(ChapterOfflineSession::getSessionDate)
        );
        return sessions.stream().map(this::convertToVO).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.Map<String, Object> getCourseAttendanceStats(Long courseId) {
        assertCourseOwnerByCourseId(courseId);
        List<Long> chapterIds = chapterRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId)
                        .select(CourseChapter::getId)
        ).stream().map(CourseChapter::getId).collect(java.util.stream.Collectors.toList());

        if (chapterIds.isEmpty()) {
            java.util.Map<String, Object> emptyStats = new java.util.HashMap<>();
            emptyStats.put("totalSessions", 0);
            emptyStats.put("totalStudents", 0);
            emptyStats.put("attendanceRate", 0.0);
            return emptyStats;
        }

        List<ChapterOfflineSession> sessions = sessionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChapterOfflineSession>()
                        .in(ChapterOfflineSession::getChapterId, chapterIds)
        );

        List<AttendanceRecord> allRecords = attendanceRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceRecord>()
                        .in(AttendanceRecord::getSessionId, sessions.stream().map(ChapterOfflineSession::getId).collect(java.util.stream.Collectors.toList()))
        );

        long presentCount = allRecords.stream().filter(r -> "PRESENT".equals(r.getStatus())).count();
        long totalExpected = sessions.size() * allRecords.stream().map(AttendanceRecord::getUserId).distinct().count();

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalSessions", sessions.size());
        stats.put("totalStudents", allRecords.stream().map(AttendanceRecord::getUserId).distinct().count());
        stats.put("presentCount", presentCount);
        stats.put("absentCount", allRecords.size() - presentCount);
        stats.put("attendanceRate", totalExpected > 0 ? (double) presentCount / totalExpected * 100 : 0.0);
        return stats;
    }
}
