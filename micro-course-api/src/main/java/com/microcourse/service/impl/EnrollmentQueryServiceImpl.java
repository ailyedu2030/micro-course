package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentQueryService;
import com.microcourse.util.MaskUtil;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;

@Service
public class EnrollmentQueryServiceImpl implements EnrollmentQueryService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final ClassesRepository classesRepository;
    private final MajorRepository majorRepository;

    public EnrollmentQueryServiceImpl(EnrollmentRepository enrollmentRepository,
                                      CourseRepository courseRepository,
                                      UserRepository userRepository,
                                      LearningProgressRepository learningProgressRepository,
                                      ClassesRepository classesRepository,
                                      MajorRepository majorRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.classesRepository = classesRepository;
        this.majorRepository = majorRepository;
    }

    @Override
    public List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getUserId, userId)
                .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
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

        // 构建查询条件
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();

        // S-04: TEACHER 数据隔离 — 强制限制只能查本人为教师的课程
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            query.setTeacherId(SecurityUtil.getCurrentUserId());
        }

        // 预处理：studentName → 子查询条件
        if (query.getStudentName() != null && !query.getStudentName().isBlank()) {
            String escaped = escapeLike(query.getStudentName().trim());
            wrapper.apply("EXISTS (SELECT 1 FROM users WHERE users.id = enrollments.user_id"
                    + " AND users.real_name LIKE {0} AND users.deleted_at IS NULL)", "%" + escaped + "%");
        }

        // P0-4 / P3: class name → EXISTS 子查询（替代多次内存查询，减少 DB round-trip）
        if (query.getClassName() != null && !query.getClassName().isBlank()) {
            String escaped = escapeLike(query.getClassName().trim());
            wrapper.apply("EXISTS (SELECT 1 FROM users u2 JOIN classes c2 ON u2.class_id = c2.id"
                    + " WHERE u2.id = enrollments.user_id AND c2.name LIKE {0}"
                    + " AND c2.deleted_at IS NULL AND u2.deleted_at IS NULL)", "%" + escaped + "%");
        }

        // P0-4 / P3: majorName → EXISTS 子查询
        if (query.getMajorName() != null && !query.getMajorName().isBlank()) {
            String escaped = escapeLike(query.getMajorName().trim());
            wrapper.apply("EXISTS (SELECT 1 FROM users u3 JOIN majors m3 ON u3.major_id = m3.id"
                    + " WHERE u3.id = enrollments.user_id AND m3.name LIKE {0}"
                    + " AND m3.deleted_at IS NULL AND u3.deleted_at IS NULL)", "%" + escaped + "%");
        }

        // teacherId / courseName 过滤
        if (query.getTeacherId() != null) {
            wrapper.apply("EXISTS (SELECT 1 FROM courses c4"
                    + " WHERE c4.id = enrollments.course_id AND c4.teacher_id = {0}"
                    + " AND c4.deleted_at IS NULL)", query.getTeacherId());
        } else if (query.getCourseName() != null && !query.getCourseName().isBlank()) {
            String escaped = escapeLike(query.getCourseName().trim());
            wrapper.apply("EXISTS (SELECT 1 FROM courses c5"
                    + " WHERE c5.id = enrollments.course_id AND c5.title LIKE {0}"
                    + " AND c5.deleted_at IS NULL)", "%" + escaped + "%");
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            // P1-C: ENROLLED 和 APPROVED 语义等价，查询时同时匹配兼容存量与未来迁移
            if (EnrollmentStatus.LEGACY_ENROLLED_VALUE.equals(query.getStatus()) || EnrollmentStatus.APPROVED.getValue().equals(query.getStatus())) {
                wrapper.in(Enrollment::getEnrollmentStatus, EnrollmentStatus.LEGACY_ENROLLED_VALUE, EnrollmentStatus.APPROVED.getValue());
            } else {
                wrapper.eq(Enrollment::getEnrollmentStatus, query.getStatus());
            }
        } else {
            // 默认排除已取消，与 getMyEnrollments 口径一致
            wrapper.ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
        }
        wrapper.orderByDesc(Enrollment::getEnrolledAt);

        IPage<Enrollment> pageResult = enrollmentRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<EnrollmentVO> voList = convertToVOList(pageResult.getRecords());
        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size) {
        // S-03: TEACHER 数据隔离 — 非 ADMIN 的 TEACHER 只能查自己课程的学员数据
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            Course course = courseRepository.selectById(courseId);
            if (course == null || !course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权查看此课程的学员数据");
            }
        }
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .notIn(Enrollment::getEnrollmentStatus,
                        EnrollmentStatus.CANCELLED.getValue(),
                        EnrollmentStatus.WAITLIST.getValue(),
                        EnrollmentStatus.DROPPED.getValue(),
                        EnrollmentStatus.REJECTED.getValue())
                .orderByDesc(Enrollment::getEnrolledAt);
        IPage<Enrollment> pageResult = enrollmentRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<EnrollmentVO> voList = convertToVOList(pageResult.getRecords());
        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    @Override
    public List<EnrollmentVO> getCourseEnrollments(Long courseId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .notIn(Enrollment::getEnrollmentStatus,
                        EnrollmentStatus.CANCELLED.getValue(),
                        EnrollmentStatus.WAITLIST.getValue(),
                        EnrollmentStatus.DROPPED.getValue(),
                        EnrollmentStatus.REJECTED.getValue())
                .orderByDesc(Enrollment::getEnrolledAt)
                .last("LIMIT 200");
        List<Enrollment> enrollments = enrollmentRepository.selectList(wrapper);
        return convertToVOList(enrollments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId) {
        // DF-001 修复:用 MyBatis-Plus Page 参数化分页,避免字符串拼接造成注入风险
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Enrollment> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, Math.max(1, Math.min(limit, 100)));
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .in(Enrollment::getEnrollmentStatus, EnrollmentStatus.LEGACY_ENROLLED_VALUE, EnrollmentStatus.APPROVED.getValue())
                .orderByDesc(Enrollment::getProgress);
        IPage<Enrollment> paged =
                enrollmentRepository.selectPage(page, wrapper);
        List<Enrollment> enrollments = paged.getRecords();

        Set<Long> userIds = enrollments.stream()
                .map(Enrollment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        List<EnrollmentRankingVO> result = new ArrayList<>();
        int rank = 1;
        for (Enrollment e : enrollments) {
            EnrollmentRankingVO vo = new EnrollmentRankingVO();
            vo.setRank(rank);
            vo.setProgress(e.getProgress());
            vo.setCompleted(e.getCompleted());

            boolean isCurrentUser = currentUserId != null && e.getUserId().equals(currentUserId);
            if (isCurrentUser) {
                // Round 11-1 数据隔离：仅向本人回显其真实 userId（用于前端高亮"我"），
                // 其余榜单成员 userId 置 null，避免暴露可枚举的真实用户主键。
                vo.setUserId(e.getUserId());
                User user = userMap.get(e.getUserId());
                vo.setUserName(user != null ? user.getRealName() : "匿名");
            } else {
                vo.setUserId(null);
                vo.setUserName("匿名");
            }
            vo.setIsCurrentUser(isCurrentUser);
            result.add(vo);
            rank++;
        }
        return result;
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
        // R12 数据隔离分级：校内透明、严格隔离
        //   - 校内管理岗（ADMIN / ACADEMIC / TEACHER）：完整（教学管理需要）
        //   - 本人：完整（自查需要）
        //   - 其他角色（如 STUDENT 查 STUDENT）：脱敏
        boolean canSeeReal = SecurityUtil.isAdmin()
                || SecurityUtil.hasRole("ACADEMIC")
                || SecurityUtil.hasRole("TEACHER")
                || SecurityUtil.isOwnerOrAdmin(userId);
        if (canSeeReal) {
            vo.setEmail(user.getEmail());
            vo.setPhone(user.getPhone());
        } else {
            vo.setEmail(MaskUtil.maskEmail(user.getEmail()));
            vo.setPhone(MaskUtil.maskPhone(user.getPhone()));
        }
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

    @Override
    public void exportEnrollments(Long courseId, HttpServletResponse response) throws IOException {
        if (SecurityUtil.hasRole("TEACHER")) {
            Course course = courseRepository.selectById(courseId);
            if (course == null) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
            Long currentUserId = SecurityUtil.getCurrentUserId();
            if (!course.getTeacherId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        List<EnrollmentVO> enrollments = getCourseEnrollments(courseId);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=enrollments_" + courseId + ".xlsx");

        ExcelWriter writer = ExcelUtil.getWriter(true);
        try {
            writer.addHeaderAlias("id", "选课ID");
            writer.addHeaderAlias("courseId", "课程ID");
            writer.addHeaderAlias("courseName", "课程名称");
            writer.addHeaderAlias("userId", "用户ID");
            writer.addHeaderAlias("userName", "学生姓名");
            writer.addHeaderAlias("progress", "学习进度(%)");
            writer.addHeaderAlias("completed", "是否完成");
            writer.addHeaderAlias("finalScore", "总评成绩");
            writer.addHeaderAlias("finalGrade", "成绩等级");
            writer.addHeaderAlias("enrollmentStatus", "选课状态");
            writer.addHeaderAlias("sourceChannel", "选课来源");
            writer.addHeaderAlias("enrolledAt", "选课时间");
            writer.addHeaderAlias("completedAt", "完成时间");

            writer.write(enrollments, true);
            writer.flush(response.getOutputStream());
        } finally {
            writer.close();
        }
    }

    // ---- Private helpers ---- //

    private List<EnrollmentVO> convertToVOList(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return new ArrayList<>();
        }
        // N+1 修复：批量预加载 course 和 user
        Set<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> userIds = enrollments.stream()
                .map(Enrollment::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, User> userMap = new HashMap<>();

        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        // 预加载 learning_progress：批量查询所有最新 lastWatchAt
        Map<String, LocalDateTime> lastWatchMap = new HashMap<>();
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
        Map<Long, User> teacherMap = new HashMap<>();
        Set<Long> teacherIds = courseMap.values().stream()
                .map(Course::getTeacherId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds).forEach(t -> teacherMap.put(t.getId(), t));
        }

        // P0-3: 预加载 classes 和 majors（批量避免 N+1）
        Set<Long> classIds = userMap.values().stream()
                .map(User::getClassId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> majorIds = userMap.values().stream()
                .map(User::getMajorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Classes> classMap = new HashMap<>();
        Map<Long, Major> majorMap = new HashMap<>();
        if (!classIds.isEmpty()) {
            classesRepository.selectBatchIds(classIds).forEach(c -> classMap.put(c.getId(), c));
        }
        if (!majorIds.isEmpty()) {
            majorRepository.selectBatchIds(majorIds).forEach(m -> majorMap.put(m.getId(), m));
        }

        final Map<Long, Course> finalCourseMap = courseMap;
        final Map<Long, User> finalUserMap = userMap;
        final Map<Long, User> finalTeacherMap = teacherMap;
        final Map<String, LocalDateTime> finalLastWatchMap = lastWatchMap;
        final Map<Long, Classes> finalClassMap = classMap;
        final Map<Long, Major> finalMajorMap = majorMap;

        return enrollments.stream()
                .map(e -> convertToVO(e, finalCourseMap, finalUserMap, finalTeacherMap, finalLastWatchMap, finalClassMap, finalMajorMap))
                .collect(Collectors.toList());
    }

    private EnrollmentVO convertToVO(Enrollment enrollment, Map<Long, Course> courseMap,
                                     Map<Long, User> userMap,
                                     Map<Long, User> teacherMap,
                                     Map<String, LocalDateTime> lastWatchMap,
                                     Map<Long, Classes> classMap,
                                     Map<Long, Major> majorMap) {
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
        vo.setBundleId(enrollment.getBundleId());
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

    /** LIKE 通配符转义,防 DF-002 LIKE 注入 */
    private static String escapeLike(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
