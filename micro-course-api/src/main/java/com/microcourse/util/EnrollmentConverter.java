package com.microcourse.util;

import com.microcourse.dto.EnrollmentVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enrollment → EnrollmentVO 转换工具 (从 EnrollmentServiceImpl 提取以减少 800 行)
 *
 * 【N+1 修复】convertToVO 单条转换时逐个调用 selectById，若调用方在循环中逐条转换
 * 则产生 N+1 查询。提供 convertToVOList 批量方法，先收集所有 ID 后批量查询，消除 N+1。
 */
public final class EnrollmentConverter {

    private EnrollmentConverter() {}

    /**
     * 完整转换 (含 course/user/major/class 名称预加载)
     */
    public static EnrollmentVO convertToVO(Enrollment enrollment,
                                           CourseRepository courseRepository,
                                           UserRepository userRepository,
                                           ClassesRepository classesRepository,
                                           MajorRepository majorRepository) {
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

    /**
     * 批量转换 — 预收集所有 courseId / userId / classId / majorId，
     * 一次批量查询后组装，避免 N+1。
     * 调用方从循环调用 convertToVO 改用此方法。
     */
    public static List<EnrollmentVO> convertToVOList(List<Enrollment> enrollments,
                                                      CourseRepository courseRepository,
                                                      UserRepository userRepository,
                                                      ClassesRepository classesRepository,
                                                      MajorRepository majorRepository) {
        if (enrollments == null || enrollments.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 收集所有关联 ID
        Set<Long> courseIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Enrollment e : enrollments) {
            if (e.getCourseId() != null) courseIds.add(e.getCourseId());
            if (e.getUserId() != null) userIds.add(e.getUserId());
        }

        // 2. 批量查询
        Map<Long, Course> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds)
                    .forEach(c -> courseMap.put(c.getId(), c));
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds)
                    .forEach(u -> userMap.put(u.getId(), u));
        }
        // 收集教师 ID 和 class/major ID
        Set<Long> teacherIds = new HashSet<>();
        Set<Long> classIds = new HashSet<>();
        Set<Long> majorIds = new HashSet<>();
        for (Course c : courseMap.values()) {
            if (c.getTeacherId() != null) teacherIds.add(c.getTeacherId());
        }
        for (User u : userMap.values()) {
            if (u.getClassId() != null) classIds.add(u.getClassId());
            if (u.getMajorId() != null) majorIds.add(u.getMajorId());
        }
        Map<Long, User> teacherMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds)
                    .forEach(t -> teacherMap.put(t.getId(), t));
        }
        Map<Long, Classes> classMap = new HashMap<>();
        if (!classIds.isEmpty()) {
            classesRepository.selectBatchIds(classIds)
                    .forEach(c -> classMap.put(c.getId(), c));
        }
        Map<Long, Major> majorMap = new HashMap<>();
        if (!majorIds.isEmpty()) {
            majorRepository.selectBatchIds(majorIds)
                    .forEach(m -> majorMap.put(m.getId(), m));
        }

        // 3. 组装 VO
        List<EnrollmentVO> result = new ArrayList<>(enrollments.size());
        for (Enrollment enrollment : enrollments) {
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

            // 课程信息
            if (enrollment.getCourseId() != null) {
                Course course = courseMap.get(enrollment.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getTitle());
                    vo.setCourseTitle(course.getTitle());
                    vo.setCoverUrl(course.getCoverUrl());
                    if (course.getTeacherId() != null) {
                        User teacher = teacherMap.get(course.getTeacherId());
                        if (teacher != null) {
                            vo.setTeacherName(teacher.getRealName());
                        }
                    }
                }
            }
            // 用户信息
            if (enrollment.getUserId() != null) {
                User user = userMap.get(enrollment.getUserId());
                if (user != null) {
                    vo.setUserName(user.getRealName());
                    vo.setUsername(user.getUsername());
                    vo.setRealName(user.getRealName());
                    if (user.getClassId() != null) {
                        Classes cls = classMap.get(user.getClassId());
                        if (cls != null) {
                            vo.setClassName(cls.getName());
                        }
                    }
                    if (user.getMajorId() != null) {
                        Major major = majorMap.get(user.getMajorId());
                        if (major != null) {
                            vo.setMajorName(major.getName());
                        }
                    }
                }
            }
            result.add(vo);
        }
        return result;
    }
}
