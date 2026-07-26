package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.LearningProgressService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/users/{id}/learning-progress 路由别名
 *
 * <p>权限矩阵 v4.1 要求保留此路径作为向后兼容别名。
 * 实际数据查询委托给 {@link LearningProgressService#getProgressWithGuard}，
 * 与主路径 /api/learning-progress/progress 共享一致的 IDOR 防护逻辑。</p>
 *
 * <p><b>为什么独立 Controller？</b>
 * Spring MVC 类级 {@code @RequestMapping} 始终作为方法级路径前缀，
 * 无法在 {@link LearningProgressController} 内声明此别名（其类前缀为
 * {@code /api/learning-progress}，会导致路径变为
 * {@code /api/learning-progress/users/{id}/learning-progress}）。
 * 独立 Controller 是保持路径正确的唯一方式。</p>
 *
 * @see LearningProgressController
 * @see LearningProgressService#getProgressWithGuard(Long, Long, Long)
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "学习进度（别名）", description = "用户下学习进度别名 API（向后兼容）")
public class UserLearningProgressAliasController {

    private final LearningProgressService learningProgressService;

    public UserLearningProgressAliasController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    /**
     * GET /api/users/{id}/learning-progress?courseId=
     *
     * <p>权限矩阵 v4.1 路径别名。IDOR 防护下沉至
     * {@link LearningProgressService#getProgressWithGuard}：</p>
     * <ul>
     *   <li>ADMIN / ACADEMIC：无限制</li>
     *   <li>TEACHER：仅可查自己课程的学生（{@code assertTeacherOwnsCourse}）</li>
     *   <li>STUDENT：仅可查本人（非本人 → 403）</li>
     * </ul>
     *
     * @param id       目标用户 ID（{@code @PathVariable}）
     * @param courseId 课程 ID（必选）
     * @return 学习进度列表
     */
    @GetMapping("/{id}/learning-progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getLearningProgress(
            @PathVariable Long id,
            @RequestParam Long courseId) {
        Long currentUserId = getCurrentUserId();
        List<LearningProgressVO> list = learningProgressService.getProgressWithGuard(currentUserId, id, courseId);
        return R.ok(list);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Number num) {
            return num.longValue();
        }
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }
}
