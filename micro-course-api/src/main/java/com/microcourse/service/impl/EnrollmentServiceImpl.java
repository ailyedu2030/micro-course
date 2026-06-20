package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.CertificateService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final ClassesRepository classesRepository;
    private final MajorRepository majorRepository;
    private final CertificateService certificateService;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository,
                                 CourseRepository courseRepository,
                                 UserRepository userRepository,
                                 LearningProgressRepository learningProgressRepository,
                                 ClassesRepository classesRepository,
                                 MajorRepository majorRepository,
                                 CertificateService certificateService) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.certificateService = certificateService;
        this.learningProgressRepository = learningProgressRepository;
        this.classesRepository = classesRepository;
        this.majorRepository = majorRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO enroll(EnrollmentCreateRequest request) {
        // 幂等性优先检查：已选过则直接返回
        LambdaQueryWrapper<Enrollment> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Enrollment::getUserId, request.getUserId())
                .eq(Enrollment::getCourseId, request.getCourseId());
        Enrollment existingEnrollment = enrollmentRepository.selectOne(existingWrapper);
        if (existingEnrollment != null) {
            return convertToVO(existingEnrollment);
        }

        // Check course exists
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Check user exists
        User user = userRepository.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(request.getCourseId());
        enrollment.setUserId(request.getUserId());
        enrollment.setSourceChannel(request.getSourceChannel());
        enrollment.setEnrollmentStatus("ENROLLED");
        enrollment.setProgress(0.0);
        enrollment.setCompleted(false);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setUpdatedAt(LocalDateTime.now());

        try {
            enrollmentRepository.insert(enrollment);
        } catch (DuplicateKeyException e) {
            // 并发场景下 check-then-insert 幂等兜底
            LambdaQueryWrapper<Enrollment> retryWrapper = new LambdaQueryWrapper<>();
            retryWrapper.eq(Enrollment::getUserId, request.getUserId())
                    .eq(Enrollment::getCourseId, request.getCourseId());
            Enrollment existing = enrollmentRepository.selectOne(retryWrapper);
            if (existing != null) return convertToVO(existing);
            throw e;
        }
        return convertToVO(enrollment);
    }

    @Override
    public List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getUserId, userId)
                .ne(Enrollment::getEnrollmentStatus, "CANCELLED");
        if (completed != null) {
            wrapper.eq(Enrollment::getCompleted, completed);
        }
        wrapper.orderByDesc(Enrollment::getEnrolledAt);
        List<Enrollment> enrollments = enrollmentRepository.selectList(wrapper);
        return convertToVOList(enrollments);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query) {
        int page = query.getPage() != null ? query.getPage() : 0;
        int size = query.getSize() != null ? query.getSize() : 10;

        // 预处理：studentName → userIds，courseName → courseIds
        java.util.Set<Long> filterUserIds = null;
        if (query.getStudentName() != null && !query.getStudentName().isBlank()) {
            LambdaQueryWrapper<User> uWrapper = new LambdaQueryWrapper<>();
            uWrapper.like(User::getRealName, query.getStudentName().trim());
            filterUserIds = userRepository.selectList(uWrapper).stream()
                    .map(User::getId).collect(java.util.stream.Collectors.toSet());
            if (filterUserIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
        }

        // P0-4: className → classIds → userIds（服务端关联过滤）
        if (query.getClassName() != null && !query.getClassName().isBlank()) {
            LambdaQueryWrapper<Classes> clsWrapper = new LambdaQueryWrapper<>();
            clsWrapper.like(Classes::getName, query.getClassName().trim());
            java.util.Set<Long> classIds = classesRepository.selectList(clsWrapper).stream()
                    .map(Classes::getId).collect(java.util.stream.Collectors.toSet());
            if (classIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
            LambdaQueryWrapper<User> cuWrapper = new LambdaQueryWrapper<>();
            cuWrapper.in(User::getClassId, classIds);
            java.util.Set<Long> classUserIds = userRepository.selectList(cuWrapper).stream()
                    .map(User::getId).collect(java.util.stream.Collectors.toSet());
            if (classUserIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
            filterUserIds = filterUserIds != null
                    ? filterUserIds.stream().filter(classUserIds::contains).collect(java.util.stream.Collectors.toSet())
                    : classUserIds;
            if (filterUserIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
        }

        // P0-4: majorName → majorIds → userIds（服务端关联过滤）
        if (query.getMajorName() != null && !query.getMajorName().isBlank()) {
            LambdaQueryWrapper<Major> mjWrapper = new LambdaQueryWrapper<>();
            mjWrapper.like(Major::getName, query.getMajorName().trim());
            java.util.Set<Long> majorIds = majorRepository.selectList(mjWrapper).stream()
                    .map(Major::getId).collect(java.util.stream.Collectors.toSet());
            if (majorIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
            LambdaQueryWrapper<User> muWrapper = new LambdaQueryWrapper<>();
            muWrapper.in(User::getMajorId, majorIds);
            java.util.Set<Long> majorUserIds = userRepository.selectList(muWrapper).stream()
                    .map(User::getId).collect(java.util.stream.Collectors.toSet());
            if (majorUserIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
            filterUserIds = filterUserIds != null
                    ? filterUserIds.stream().filter(majorUserIds::contains).collect(java.util.stream.Collectors.toSet())
                    : majorUserIds;
            if (filterUserIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
        }

        java.util.Set<Long> filterCourseIds = null;
        // teacherId 过滤：获取该教师的所有课程
        if (query.getTeacherId() != null) {
            filterCourseIds = getCourseIdsByTeacherId(query.getTeacherId());
            if (filterCourseIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
        } else if (query.getCourseName() != null && !query.getCourseName().isBlank()) {
            LambdaQueryWrapper<Course> cWrapper = new LambdaQueryWrapper<>();
            cWrapper.like(Course::getTitle, query.getCourseName().trim());
            filterCourseIds = courseRepository.selectList(cWrapper).stream()
                    .map(Course::getId).collect(java.util.stream.Collectors.toSet());
            if (filterCourseIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
        }

        // 构建查询条件
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        if (filterUserIds != null) wrapper.in(Enrollment::getUserId, filterUserIds);
        if (filterCourseIds != null) wrapper.in(Enrollment::getCourseId, filterCourseIds);
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Enrollment::getEnrollmentStatus, query.getStatus());
        }
        wrapper.orderByDesc(Enrollment::getEnrolledAt);

        IPage<Enrollment> pageResult = enrollmentRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<EnrollmentVO> voList = convertToVOList(pageResult.getRecords());
        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    @Override
    public List<EnrollmentVO> getCourseEnrollments(Long courseId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .orderByDesc(Enrollment::getEnrolledAt);
        List<Enrollment> enrollments = enrollmentRepository.selectList(wrapper);
        return convertToVOList(enrollments);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .orderByDesc(Enrollment::getEnrolledAt);
        IPage<Enrollment> pageResult = enrollmentRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<EnrollmentVO> voList = convertToVOList(pageResult.getRecords());
        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId) {
        // DF-001 修复:用 MyBatis-Plus Page 参数化分页,避免字符串拼接造成注入风险
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Enrollment> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, Math.max(1, Math.min(limit, 100)));
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getEnrollmentStatus, "ENROLLED")
                .orderByDesc(Enrollment::getProgress);
        com.baomidou.mybatisplus.core.metadata.IPage<Enrollment> paged =
                enrollmentRepository.selectPage(page, wrapper);
        List<Enrollment> enrollments = paged.getRecords();

        java.util.Set<Long> userIds = enrollments.stream()
                .map(Enrollment::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        java.util.List<EnrollmentRankingVO> result = new java.util.ArrayList<>();
        int rank = 1;
        for (Enrollment e : enrollments) {
            EnrollmentRankingVO vo = new EnrollmentRankingVO();
            vo.setRank(rank);
            vo.setUserId(e.getUserId());
            vo.setProgress(e.getProgress());
            vo.setCompleted(e.getCompleted());

            boolean isCurrentUser = currentUserId != null && e.getUserId().equals(currentUserId);
            if (isCurrentUser) {
                User user = userMap.get(e.getUserId());
                vo.setUserName(user != null ? user.getRealName() : "匿名");
            } else {
                vo.setUserName("匿名");
            }
            vo.setIsCurrentUser(isCurrentUser);
            result.add(vo);
            rank++;
        }
        return result;
    }

    private List<EnrollmentVO> convertToVOList(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        // N+1 修复：批量预加载 course 和 user
        java.util.Set<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> userIds = enrollments.stream()
                .map(Enrollment::getUserId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();

        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        // 预加载 learning_progress：批量查询所有最新 lastWatchAt
        java.util.Map<String, LocalDateTime> lastWatchMap = new java.util.HashMap<>();
        if (!userIds.isEmpty() && !courseIds.isEmpty()) {
            LambdaQueryWrapper<LearningProgress> lpWrapper = new LambdaQueryWrapper<>();
            lpWrapper.in(LearningProgress::getUserId, userIds)
                     .in(LearningProgress::getCourseId, courseIds)
                     .isNotNull(LearningProgress::getLastWatchAt)
                     .orderByDesc(LearningProgress::getLastWatchAt);
            List<LearningProgress> allLps = learningProgressRepository.selectList(lpWrapper);
            for (LearningProgress lp : allLps) {
                String key = lp.getUserId() + "_" + lp.getCourseId();
                if (!lastWatchMap.containsKey(key)) {
                    lastWatchMap.put(key, lp.getLastWatchAt());
                }
            }
        }

        // 预加载教师信息
        java.util.Map<Long, User> teacherMap = new java.util.HashMap<>();
        java.util.Set<Long> teacherIds = courseMap.values().stream()
                .map(Course::getTeacherId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds).forEach(t -> teacherMap.put(t.getId(), t));
        }

        // P0-3: 预加载 classes 和 majors（批量避免 N+1）
        java.util.Set<Long> classIds = userMap.values().stream()
                .map(User::getClassId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> majorIds = userMap.values().stream()
                .map(User::getMajorId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Classes> classMap = new java.util.HashMap<>();
        java.util.Map<Long, Major> majorMap = new java.util.HashMap<>();
        if (!classIds.isEmpty()) {
            classesRepository.selectBatchIds(classIds).forEach(c -> classMap.put(c.getId(), c));
        }
        if (!majorIds.isEmpty()) {
            majorRepository.selectBatchIds(majorIds).forEach(m -> majorMap.put(m.getId(), m));
        }

        final java.util.Map<Long, Course> finalCourseMap = courseMap;
        final java.util.Map<Long, User> finalUserMap = userMap;
        final java.util.Map<Long, User> finalTeacherMap = teacherMap;
        final java.util.Map<String, LocalDateTime> finalLastWatchMap = lastWatchMap;
        final java.util.Map<Long, Classes> finalClassMap = classMap;
        final java.util.Map<Long, Major> finalMajorMap = majorMap;

        return enrollments.stream()
                .map(e -> convertToVO(e, finalCourseMap, finalUserMap, finalTeacherMap, finalLastWatchMap, finalClassMap, finalMajorMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        if (request.getProgress() != null) {
            enrollment.setProgress(request.getProgress());
        }
        if (request.getCompleted() != null) {
            enrollment.setCompleted(request.getCompleted());
            if (request.getCompleted()) {
                enrollment.setCompletedAt(LocalDateTime.now());
                try {
                    certificateService.issueCertificate(enrollment.getUserId(), enrollment.getCourseId());
                } catch (Exception ignored) {
                }
            }
        }
        if (request.getFinalScore() != null) {
            enrollment.setFinalScore(request.getFinalScore());
        }
        if (request.getFinalGrade() != null) {
            enrollment.setFinalGrade(request.getFinalGrade());
        }
        if (request.getEnrollmentStatus() != null) {
            enrollment.setEnrollmentStatus(request.getEnrollmentStatus());
        }

        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.updateById(enrollment);
        return convertToVO(enrollment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelEnrollment(Long id, Long currentUserId) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        // IDOR 校验(SEC-NEW-2 修复):优先使用 Controller 传入的 currentUserId,fallback 用 SecurityUtil
        Long effectiveUserId = currentUserId != null ? currentUserId : getCurrentUserId();
        boolean isAdmin = hasAdminRole();
        if (!isAdmin && !enrollment.getUserId().equals(effectiveUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        enrollment.setEnrollmentStatus("CANCELLED");
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.updateById(enrollment);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByTeacherId(Long teacherId) {
        java.util.Set<Long> courseIds = getCourseIdsByTeacherId(teacherId);
        if (courseIds.isEmpty()) {
            return 0;
        }
        return enrollmentRepository.selectCount(
            new LambdaQueryWrapper<Enrollment>()
                .in(Enrollment::getCourseId, courseIds)
                .isNull(Enrollment::getDeletedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedByTeacherId(Long teacherId) {
        java.util.Set<Long> courseIds = getCourseIdsByTeacherId(teacherId);
        if (courseIds.isEmpty()) {
            return 0;
        }
        return enrollmentRepository.selectCount(
            new LambdaQueryWrapper<Enrollment>()
                .in(Enrollment::getCourseId, courseIds)
                .eq(Enrollment::getCompleted, true)
                .isNull(Enrollment::getDeletedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public double getAvgScoreByTeacherId(Long teacherId) {
        java.util.Set<Long> courseIds = getCourseIdsByTeacherId(teacherId);
        if (courseIds.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Enrollment::getCourseId, courseIds)
               .isNotNull(Enrollment::getFinalScore)
               .isNull(Enrollment::getDeletedAt);
        List<Enrollment> enrollments = enrollmentRepository.selectList(wrapper);
        if (enrollments.isEmpty()) {
            return 0;
        }
        return enrollments.stream()
            .filter(e -> e.getFinalScore() != null)
            .mapToDouble(e -> e.getFinalScore().doubleValue())
            .average()
            .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDetailVO getStudentDetail(Long userId) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        StudentDetailVO vo = new StudentDetailVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        if (user.getClassId() != null) {
            Classes cls = classesRepository.selectById(user.getClassId());
            if (cls != null) {
                vo.setClassName(cls.getName());
            }
        }
        if (user.getMajorId() != null) {
            Major major = majorRepository.selectById(user.getMajorId());
            if (major != null) {
                vo.setMajorName(major.getName());
            }
        }
        return vo;
    }

    private EnrollmentVO convertToVO(Enrollment enrollment) {
        EnrollmentVO vo = new EnrollmentVO();
        vo.setId(enrollment.getId());
        vo.setCourseId(enrollment.getCourseId());
        vo.setUserId(enrollment.getUserId());
        vo.setProgress(enrollment.getProgress());
        vo.setCompleted(enrollment.getCompleted());
        vo.setFinalScore(enrollment.getFinalScore());
        vo.setFinalGrade(enrollment.getFinalGrade());
        vo.setEnrollmentStatus(enrollment.getEnrollmentStatus());
        vo.setSourceChannel(enrollment.getSourceChannel());
        vo.setEnrolledAt(enrollment.getEnrolledAt());
        vo.setCompletedAt(enrollment.getCompletedAt());
        vo.setUpdatedAt(enrollment.getUpdatedAt());

        // Load course info including teacher
        if (enrollment.getCourseId() != null) {
            Course course = courseRepository.selectById(enrollment.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
                vo.setCourseTitle(course.getTitle());
                vo.setCoverUrl(course.getCoverUrl());
                if (course.getTeacherId() != null) {
                    User teacher = userRepository.selectById(course.getTeacherId());
                    if (teacher != null) {
                        vo.setTeacherName(teacher.getRealName());
                    }
                }
            }
        }

        // P0-3: 填充用户维度字段
        if (enrollment.getUserId() != null) {
            User user = userRepository.selectById(enrollment.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
                if (user.getClassId() != null) {
                    Classes cls = classesRepository.selectById(user.getClassId());
                    if (cls != null) {
                        vo.setClassName(cls.getName());
                    }
                }
                if (user.getMajorId() != null) {
                    Major major = majorRepository.selectById(user.getMajorId());
                    if (major != null) {
                        vo.setMajorName(major.getName());
                    }
                }
            }
        }

        return vo;
    }

    private EnrollmentVO convertToVO(Enrollment enrollment, java.util.Map<Long, Course> courseMap,
                                     java.util.Map<Long, User> userMap,
                                     java.util.Map<Long, User> teacherMap,
                                     java.util.Map<String, LocalDateTime> lastWatchMap,
                                     java.util.Map<Long, Classes> classMap,
                                     java.util.Map<Long, Major> majorMap) {
        EnrollmentVO vo = new EnrollmentVO();
        vo.setId(enrollment.getId());
        vo.setCourseId(enrollment.getCourseId());
        vo.setUserId(enrollment.getUserId());
        vo.setProgress(enrollment.getProgress());
        vo.setCompleted(enrollment.getCompleted());
        vo.setFinalScore(enrollment.getFinalScore());
        vo.setFinalGrade(enrollment.getFinalGrade());
        vo.setEnrollmentStatus(enrollment.getEnrollmentStatus());
        vo.setSourceChannel(enrollment.getSourceChannel());
        vo.setEnrolledAt(enrollment.getEnrolledAt());
        vo.setCompletedAt(enrollment.getCompletedAt());
        vo.setUpdatedAt(enrollment.getUpdatedAt());

        // Load course name（使用预加载的 Map）
        if (enrollment.getCourseId() != null) {
            Course course = courseMap.get(enrollment.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
                vo.setCourseTitle(course.getTitle());
                vo.setCoverUrl(course.getCoverUrl());
                if (course.getTeacherId() != null && teacherMap != null) {
                    User teacher = teacherMap.get(course.getTeacherId());
                    if (teacher != null) {
                        vo.setTeacherName(teacher.getRealName());
                    }
                }
            }
        }

        // P0-3: 填充用户维度字段（使用预加载的 Map）
        if (enrollment.getUserId() != null) {
            User user = userMap.get(enrollment.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
                // 关联 class 名称
                if (user.getClassId() != null && classMap != null) {
                    Classes cls = classMap.get(user.getClassId());
                    if (cls != null) {
                        vo.setClassName(cls.getName());
                    }
                }
                // 关联 major 名称
                if (user.getMajorId() != null && majorMap != null) {
                    Major major = majorMap.get(user.getMajorId());
                    if (major != null) {
                        vo.setMajorName(major.getName());
                    }
                }
            }
        }

        // Load lastWatchAt from learning_progress
        if (enrollment.getUserId() != null && enrollment.getCourseId() != null) {
            LocalDateTime lastWatchAt = lastWatchMap.get(
                    enrollment.getUserId() + "_" + enrollment.getCourseId());
            vo.setLastWatchAt(lastWatchAt);
        }

        return vo;
    }

    /** P1-3: getCurrentUserId 类型安全 —— 兼容 Long / String / Number 类型 principal */
    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Number) return ((Number) principal).longValue();
        if (principal instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    private boolean hasAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (granted.getAuthority().equals("ROLE_ADMIN")) return true;
        }
        return false;
    }

    /** P2: 提取教师课程 ID 集合的公共方法，消除重复代码 */
    private java.util.Set<Long> getCourseIdsByTeacherId(Long teacherId) {
        LambdaQueryWrapper<Course> cWrapper = new LambdaQueryWrapper<>();
        cWrapper.eq(Course::getTeacherId, teacherId)
                .isNull(Course::getDeletedAt)
                .select(Course::getId);
        return courseRepository.selectList(cWrapper).stream()
                .map(Course::getId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
