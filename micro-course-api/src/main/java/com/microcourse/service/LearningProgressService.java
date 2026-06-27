package com.microcourse.service;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;

import java.util.List;
import java.util.Map;

public interface LearningProgressService {

    List<LearningProgressVO> getByUserAndCourse(Long userId, Long courseId);

    /**
     * R8 P0-3: 批量获取用户在多门课程中的学习进度（解决 MyCourses N+1）。
     */
    List<LearningProgressVO> batchGetByUserAndCourses(Long userId, List<Long> courseIds);

    void updateProgress(Long id, Long userId, ProgressUpdateRequest request);

    LearningProgressVO create(ProgressCreateRequest request);

    Map<String, Object> getCourseCompletion(Long userId, Long courseId);

    /**
     * P0-5: 聚合用户所有课程的完成进度
     * @param userId 用户ID
     * @return { courseId: { completion, progress, completedCount, totalLessons } }
     */
    Map<String, Object> getAllCourseCompletions(Long userId);

    /**
     * 聚合用户所有课程的学习天数（distinct date）
     * @param userId 用户ID
     * @return { totalDays: N }
     */
    Map<String, Object> getStudyDays(Long userId);

    /**
     * 聚合用户所有课程的总观看时长（秒）
     * @param userId 用户ID
     * @return { totalSeconds: N }
     */
    Map<String, Object> getTotalTime(Long userId);

    /**
     * 校验教师是否为指定课程的授课教师
     * @param teacherId 教师用户ID
     * @param courseId 课程ID
     * @throws BusinessException 非本人课程时抛 NO_PERMISSION
     */
    void assertTeacherOwnsCourse(Long teacherId, Long courseId);
}
