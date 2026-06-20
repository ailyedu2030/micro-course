package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.*;
import com.microcourse.entity.ClassSchedule;
import com.microcourse.entity.Course;
import com.microcourse.entity.TeachingClass;
import com.microcourse.entity.TeachingClassStudent;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassScheduleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.TeachingClassRepository;
import com.microcourse.repository.TeachingClassStudentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.TeachingClassService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeachingClassServiceImpl implements TeachingClassService {

    private static final Logger log = LoggerFactory.getLogger(TeachingClassServiceImpl.class);

    /** P1-1: 学生状态白名单 */
    private static final Set<String> VALID_STUDENT_STATUSES = Set.of("ACTIVE", "DISABLED", "SUSPENDED");

    private final TeachingClassRepository teachingClassRepository;
    private final TeachingClassStudentRepository teachingClassStudentRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public TeachingClassServiceImpl(TeachingClassRepository teachingClassRepository,
                                   TeachingClassStudentRepository teachingClassStudentRepository,
                                   ClassScheduleRepository classScheduleRepository,
                                   CourseRepository courseRepository,
                                   UserRepository userRepository) {
        this.teachingClassRepository = teachingClassRepository;
        this.teachingClassStudentRepository = teachingClassStudentRepository;
        this.classScheduleRepository = classScheduleRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PageResult<TeachingClassVO> page(int page, int size, Long teacherId, Long courseId,
                                           String semester, Integer status) {
        // SECURITY: TEACHER 只能看到自己的教学班
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC")) {
            teacherId = currentUserId;
        }

        // Controller 传入 0-based page, MyBatis-Plus Page 使用 1-based, 此处 +1 转换
        Page<TeachingClass> ipage = new Page<>(page + 1, size);
        LambdaQueryWrapper<TeachingClass> wrapper = new LambdaQueryWrapper<>();

        if (teacherId != null) {
            wrapper.eq(TeachingClass::getTeacherId, teacherId);
        }
        if (courseId != null) {
            wrapper.eq(TeachingClass::getCourseId, courseId);
        }
        if (semester != null && !semester.isBlank()) {
            wrapper.eq(TeachingClass::getSemester, semester);
        }
        if (status != null) {
            wrapper.eq(TeachingClass::getStatus, status);
        }
        wrapper.orderByDesc(TeachingClass::getCreatedAt);

        IPage<TeachingClass> result = teachingClassRepository.selectPage(ipage, wrapper);
        // RES-NEW-1 修复:批量预加载 course 和 teacher,避免 N+1
        List<TeachingClass> records = result.getRecords();
        java.util.Set<Long> courseIds = records.stream().map(TeachingClass::getCourseId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> teacherIds = records.stream().map(TeachingClass::getTeacherId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Course> courseMap = courseIds.isEmpty() ? java.util.Collections.emptyMap() :
                courseRepository.selectBatchIds(courseIds).stream().collect(java.util.stream.Collectors.toMap(Course::getId, c -> c));
        java.util.Map<Long, User> teacherMap = teacherIds.isEmpty() ? java.util.Collections.emptyMap() :
                userRepository.selectBatchIds(teacherIds).stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<TeachingClassVO> voList = records.stream()
                .map(tc -> convertToVO(tc, courseMap.get(tc.getCourseId()), teacherMap.get(tc.getTeacherId())))
                .collect(Collectors.toList());

        PageResult<TeachingClassVO> pageResult = new PageResult<>();
        pageResult.setItems(voList);
        pageResult.setPage((int) result.getCurrent() - 1);
        pageResult.setSize((int) result.getSize());
        pageResult.setTotalElements(result.getTotal());
        pageResult.setTotalPages(result.getPages());
        return pageResult;
    }

    @Override
    public TeachingClassVO getById(Long id) {
        TeachingClass tc = teachingClassRepository.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        return convertToVO(tc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeachingClassVO create(TeachingClassCreateRequest req) {
        Course course = courseRepository.selectById(req.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        if (req.getTeacherId() != null) {
            User teacher = userRepository.selectById(req.getTeacherId());
            if (teacher == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            if (teacher.getRole() != UserRole.TEACHER) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }

        TeachingClass tc = new TeachingClass();
        tc.setCourseId(req.getCourseId());
        tc.setTeacherId(req.getTeacherId());
        tc.setName(req.getName());
        tc.setMaxStudents(req.getMaxStudents());
        tc.setStudentCount(0);
        tc.setSchedule(req.getSchedule());
        tc.setLocation(req.getLocation());
        tc.setSemester(req.getSemester());
        tc.setStatus(0);
        tc.setCreatedAt(LocalDateTime.now());
        tc.setUpdatedAt(LocalDateTime.now());
        tc.setVersion(0);
        teachingClassRepository.insert(tc);

        if (req.getClassSchedules() != null && !req.getClassSchedules().isEmpty()) {
            for (ClassScheduleDTO dto : req.getClassSchedules()) {
                ClassSchedule cs = new ClassSchedule();
                cs.setClassId(tc.getId());
                cs.setDayOfWeek(dto.getDayOfWeek());
                cs.setStartPeriod(dto.getStartPeriod());
                cs.setEndPeriod(dto.getEndPeriod());
                cs.setStartTime(dto.getStartTime());
                cs.setEndTime(dto.getEndTime());
                cs.setLocation(dto.getLocation());
                cs.setWeekPattern(dto.getWeekPattern());
                cs.setCustomWeeks(dto.getCustomWeeks());
                classScheduleRepository.insert(cs);
            }
        }

        return convertToVO(tc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeachingClassVO update(Long id, TeachingClassUpdateRequest req) {
        TeachingClass tc = teachingClassRepository.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }

        // SECURITY: 只有班级教师或 ADMIN 可修改教学班
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin() && !currentUserId.equals(tc.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权修改该教学班");
        }

        if (req.getCourseId() != null) {
            tc.setCourseId(req.getCourseId());
        }
        if (req.getTeacherId() != null) {
            tc.setTeacherId(req.getTeacherId());
        }
        if (req.getName() != null) {
            tc.setName(req.getName());
        }
        if (req.getMaxStudents() != null) {
            tc.setMaxStudents(req.getMaxStudents());
        }
        if (req.getSchedule() != null) {
            tc.setSchedule(req.getSchedule());
        }
        if (req.getLocation() != null) {
            tc.setLocation(req.getLocation());
        }
        if (req.getSemester() != null) {
            tc.setSemester(req.getSemester());
        }
        if (req.getStatus() != null) {
            tc.setStatus(req.getStatus());
        }
        tc.setUpdatedAt(LocalDateTime.now());
        teachingClassRepository.updateById(tc);

        if (req.getClassSchedules() != null) {
            classScheduleRepository.deleteByClassId(id);
            for (ClassScheduleDTO dto : req.getClassSchedules()) {
                ClassSchedule cs = new ClassSchedule();
                cs.setClassId(id);
                cs.setDayOfWeek(dto.getDayOfWeek());
                cs.setStartPeriod(dto.getStartPeriod());
                cs.setEndPeriod(dto.getEndPeriod());
                cs.setStartTime(dto.getStartTime());
                cs.setEndTime(dto.getEndTime());
                cs.setLocation(dto.getLocation());
                cs.setWeekPattern(dto.getWeekPattern());
                cs.setCustomWeeks(dto.getCustomWeeks());
                classScheduleRepository.insert(cs);
            }
        }

        return convertToVO(tc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TeachingClass tc = teachingClassRepository.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        classScheduleRepository.deleteByClassId(id);
        teachingClassRepository.deleteById(id);
    }

    @Override
    public List<TeachingClassStudentVO> getClassStudents(Long classId) {
        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }

        // SECURITY: 只有班级教师或 ADMIN/ACADEMIC 可查看学生名单
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC") && !currentUserId.equals(tc.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权查看该教学班学生名单");
        }

        List<TeachingClassStudent> students = teachingClassStudentRepository.selectActiveByClassId(classId);
        if (students.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> userIds = students.stream()
                .map(TeachingClassStudent::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return students.stream().map(s -> {
            TeachingClassStudentVO vo = new TeachingClassStudentVO();
            vo.setId(s.getId());
            vo.setClassId(s.getClassId());
            vo.setUserId(s.getUserId());
            vo.setEnrolledAt(s.getEnrolledAt());
            vo.setStatus(s.getStatus());
            vo.setClassName(tc.getName());
            User user = userMap.get(s.getUserId());
            if (user != null) {
                vo.setRealName(user.getRealName());
                vo.setStudentNo(user.getStudentNo());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStudent(Long classId, Long userId) {
        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }

        // P0-3: 越权校验 — 当前用户必须是该班级教师或管理员
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin() && !currentUserId.equals(tc.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // P1-2: 容量上限检查
        if (tc.getStudentCount() != null && tc.getMaxStudents() != null
                && tc.getStudentCount() >= tc.getMaxStudents()) {
            throw new BusinessException(ErrorCode.CLASS_FULL);
        }

        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (teachingClassStudentRepository.existsByClassIdAndUserId(classId, userId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
        }

        // CON-NEW-4 修复:并发安全选课 — 用原子 SQL 递增 count,并捕获 DuplicateKeyException
        TeachingClassStudent record = new TeachingClassStudent();
        record.setClassId(classId);
        record.setUserId(userId);
        record.setEnrolledAt(LocalDateTime.now());
        record.setStatus("ENROLLED");
        try {
            teachingClassStudentRepository.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            // DB uk_tcs_class_user 兜底:并发选课,DB UNIQUE 阻止第二条,降级为已存在
            throw new BusinessException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
        }

        // 原子递增 student_count,避免 Java 侧读-改-写导致的并发计数偏差
        teachingClassRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TeachingClass>()
                        .eq(TeachingClass::getId, classId)
                        .setSql("student_count = COALESCE(student_count, 0) + 1")
                        .set(TeachingClass::getUpdatedAt, LocalDateTime.now()));

        log.info("添加学生: classId={}, userId={}, operator={}", classId, userId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long classId, Long userId) {
        // P0-3: 越权校验
        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin() && !currentUserId.equals(tc.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        TeachingClassStudent record = teachingClassStudentRepository.selectList(
                new LambdaQueryWrapper<TeachingClassStudent>()
                        .eq(TeachingClassStudent::getClassId, classId)
                        .eq(TeachingClassStudent::getUserId, userId)
        ).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        record.setStatus("DROPPED");
        teachingClassStudentRepository.updateById(record);

        // CON-NEW-5 修复:原子递减,使用 GREATEST 避免负数
        teachingClassRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TeachingClass>()
                        .eq(TeachingClass::getId, classId)
                        .setSql("student_count = GREATEST(COALESCE(student_count, 0) - 1, 0)")
                        .set(TeachingClass::getUpdatedAt, LocalDateTime.now()));

        log.info("移除学生: classId={}, userId={}, operator={}", classId, userId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudentStatus(Long classId, Long userId, String status) {
        // P1-1: 状态白名单校验
        if (status == null || !VALID_STUDENT_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
        }

        // P0-3: 越权校验
        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin() && !currentUserId.equals(tc.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        TeachingClassStudent record = teachingClassStudentRepository.selectList(
                new LambdaQueryWrapper<TeachingClassStudent>()
                        .eq(TeachingClassStudent::getClassId, classId)
                        .eq(TeachingClassStudent::getUserId, userId)
        ).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        String oldStatus = record.getStatus();
        record.setStatus(status);
        teachingClassStudentRepository.updateById(record);

        log.info("修改学生状态: classId={}, userId={}, {} -> {}, operator={}", classId, userId, oldStatus, status, currentUserId);
    }

    private TeachingClassVO convertToVO(TeachingClass tc) {
        return convertToVO(tc, null, null);
    }

    /**
     * RES-NEW-1 批量预加载版本:course/teacher 从传入的 Map 查,避免 N+1
     */
    private TeachingClassVO convertToVO(TeachingClass tc, Course course, User teacher) {
        TeachingClassVO vo = new TeachingClassVO();
        vo.setId(tc.getId());
        vo.setCourseId(tc.getCourseId());
        vo.setTeacherId(tc.getTeacherId());
        vo.setName(tc.getName());
        vo.setMaxStudents(tc.getMaxStudents());
        vo.setStudentCount(tc.getStudentCount());
        vo.setSchedule(tc.getSchedule());
        vo.setLocation(tc.getLocation());
        vo.setSemester(tc.getSemester());
        vo.setStatus(tc.getStatus());
        vo.setStatusLabel(resolveStatusLabel(tc.getStatus()));
        vo.setCreatedAt(tc.getCreatedAt());
        vo.setUpdatedAt(tc.getUpdatedAt());
        vo.setVersion(tc.getVersion());

        if (course == null && tc.getCourseId() != null) {
            course = courseRepository.selectById(tc.getCourseId());
        }
        if (course != null) {
            vo.setCourseTitle(course.getTitle());
            vo.setCourseCoverUrl(course.getCoverUrl());
        }

        if (teacher == null && tc.getTeacherId() != null) {
            teacher = userRepository.selectById(tc.getTeacherId());
        }
        if (teacher != null) {
            vo.setTeacherName(teacher.getRealName());
        }

        return vo;
    }

    private String resolveStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待开课";
            case 1 -> "进行中";
            case 2 -> "已结课";
            default -> "未知";
        };
    }
}