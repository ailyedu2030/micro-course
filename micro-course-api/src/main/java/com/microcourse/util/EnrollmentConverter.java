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

/**
 * Enrollment → EnrollmentVO 转换工具 (从 EnrollmentServiceImpl 提取以减少 800 行)
 */
public final class EnrollmentConverter {

    private EnrollmentConverter() {}

    /**
     * 完整转换 (含 course/user/major/class 名称预加载, 避免 N+1)
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
}
