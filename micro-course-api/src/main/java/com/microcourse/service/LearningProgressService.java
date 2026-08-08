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

    /**
     * 获取学习进度（含 IDOR 防护：ADMIN 无限制，TEACHER 需校验课程归属，STUDENT 仅本人）
     * @param currentUserId 当前登录用户ID
     * @param targetUserId 目标用户ID
     * @param courseId 课程ID
     * @return 学习进度列表
     */
    List<LearningProgressVO> getProgressWithGuard(Long currentUserId, Long targetUserId, Long courseId);

    /**
     * 获取课程完成度（含 IDOR 防护：ADMIN 无限制，TEACHER 需校验课程归属，STUDENT 仅本人）
     * @param currentUserId 当前登录用户ID
     * @param userId 目标用户ID
     * @param courseId 课程ID（可选，为 null 时返回所有课程完成度）
     * @return 完成度 Map
     */
    Map<String, Object> getCourseCompletionWithGuard(Long currentUserId, Long userId, Long courseId);

    /**
     * G3-P0-5: 上报课时播放进度 → 服务端计算 video_progress 并写入 learning_progress。
     * <p>
     * 供 evaluateFlow 的 SKIP_IF_KNOWN 服务端读取（设计决策 3）：
     * 学生播放器每次翻页/音频结束调用，纯 PPT/HTML 学习场景得以真正命中 SKIP 规则。
     * 计算 {@code video_progress = min(100, round(played/total*100))}；total &lt;= 0 时
     * 仅刷新 last_watch_at 不清零已有进度。记录不存在则幂等创建。
     * </p>
     *
     * @param userId        当前用户 ID（SecurityContext 派生，不信任客户端）
     * @param courseId      课程 ID（path）
     * @param sectionId     课时 ID（path，对应 learning_progress.lesson_id）
     * @param playedSeconds 已播时长（秒）
     * @param totalSeconds  总时长（秒）
     */
    void updateVideoProgress(Long userId, Long courseId, Long sectionId,
                             Integer playedSeconds, Integer totalSeconds);
}
