package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Video;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoAccessService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 视频访问权限服务（Round 8-1 修复）。
 *
 * <p>解决"未选课也能看视频"的商业致命缺陷：在视频播放链路（sign / play / 元数据 / HLS 流）
 * 上补齐选课校验。本类仅负责"判定是否允许访问"，HTTP 语义（403 NOT_ENROLLED + 友好提示）
 * 由调用方 Controller 统一处理。</p>
 *
 * <p>权限矩阵（"用户体验优先"——合法用户零感、未选课友好拦截）：</p>
 * <ul>
 *   <li>ADMIN / ACADEMIC：始终允许（运营/教务需要全量访问）。</li>
 *   <li>TEACHER：允许（课主级校验由上传/管理链路的 {@code assertCourseOwnership} 负责；
 *       观看链路对教师放行，避免误伤既有教学流）。</li>
 *   <li>STUDENT（及其余角色）：必须存在有效选课记录（ENROLLED / APPROVED / COMPLETED）。</li>
 * </ul>
 *
 * <p>实现约束（对齐项目宪法）：不使用 Lombok（改用手动 SLF4J Logger），
 * 依赖通过构造器注入（不使用 @Autowired 字段注入）。</p>
 */
@Service
public class VideoAccessServiceImpl implements VideoAccessService {

    private static final Logger log = LoggerFactory.getLogger(VideoAccessServiceImpl.class);

    private final EnrollmentRepository enrollmentRepository;
    private final VideoRepository videoRepository;

    public VideoAccessServiceImpl(EnrollmentRepository enrollmentRepository,
                                  VideoRepository videoRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.videoRepository = videoRepository;
    }

    /**
     * 检查当前用户是否可访问指定课程的视频。
     *
     * <p>角色判定基于 {@link SecurityUtil}（从 SecurityContext 读取），故无需调用方传入角色字符串。</p>
     *
     * @param userId   当前登录用户 ID（学生选课校验使用）
     * @param courseId 视频所属课程 ID
     * @return {@link VideoAccessService.AccessResult}，包含允许/拒绝及原因
     */
    @Override
    public VideoAccessService.AccessResult checkVideoAccess(Long userId, Long courseId) {
        // 1. 管理员 / 教务：始终允许
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) {
            return VideoAccessService.AccessResult.allowed("管理员/教务");
        }

        // 2. 教师：放行（课主级校验由上传/管理链路负责，观看链路不误伤教师）
        if (SecurityUtil.hasRole("TEACHER")) {
            return VideoAccessService.AccessResult.allowed("教师");
        }

        // 3. 学生（及其余角色）：必须已选课
        if (isEnrolled(userId, courseId)) {
            return VideoAccessService.AccessResult.allowed("已选课");
        }
        if (log.isDebugEnabled()) {
            log.debug("[VideoAccess] 拒绝访问：userId={} 未选课 courseId={}", userId, courseId);
        }
        return VideoAccessService.AccessResult.denied("请先选课");
    }

    /**
     * 检查学生是否有权限访问指定视频。
     * 仅 STUDENT 角色需要检查；ADMIN / ACADEMIC / TEACHER 直接放行。
     */
    @Override
    public void checkStudentAccess(Long videoId) {
        if (!SecurityUtil.hasRole("STUDENT")) {
            return; // 非学生（教师/管理员/教务）放行
        }
        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        AccessResult result = checkVideoAccess(SecurityUtil.getCurrentUserId(), video.getCourseId());
        if (!result.allowed) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "请先选课后再观看视频");
        }
    }

    /**
     * 判定用户对课程是否持有有效选课记录。
     *
     * <p>有效状态：{@code ENROLLED}（历史在读值）/ {@code APPROVED}（契约在读值）/ {@code COMPLETED}（已完成，允许复习）。
     * 逻辑删除（{@code deleted_at IS NULL}）由实体 {@code @TableLogic} 自动追加，无需手动拼接。</p>
     */
    @Override
    public boolean isEnrolled(Long userId, Long courseId) {
        if (userId == null || courseId == null) {
            return false;
        }
        Long count = enrollmentRepository.selectCount(
                new QueryWrapper<Enrollment>()
                        .eq("user_id", userId)
                        .eq("course_id", courseId)
                        .in("enrollment_status",
                                EnrollmentStatus.legacyActiveWith(EnrollmentStatus.COMPLETED.getValue()))  // 历史兼容 + 允许复习
        );
        return count != null && count > 0;
    }
}
