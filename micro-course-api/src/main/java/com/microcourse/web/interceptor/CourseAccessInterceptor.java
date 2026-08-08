package com.microcourse.web.interceptor;

import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 课件写操作对象级授权拦截器（Phase 9 P0-1/P0-2 IDOR 修复 · 纵深防御）。
 *
 * <h3>拦截范围</h3>
 * <ul>
 *   <li>{@code /api/courses/{courseId}/ppt/**}</li>
 *   <li>{@code /api/courses/{courseId}/html/**}</li>
 * </ul>
 * 仅对<b>写方法</b>（POST / PUT / DELETE / PATCH）生效；读方法（GET/HEAD/OPTIONS）
 * 直接放行，由 Controller 内显式校验（如 HtmlCoursewareController.getUnit 的
 * {@code verifyUnitOwner}）精确控制，避免破坏课件读侧既有访问语义
 * （ACADEMIC 读 / 已选课 STUDENT 读）。
 *
 * <h3>校验语义</h3>
 * 与 {@code PptCoursewareService.verifyOwner} 同源：ADMIN 通行；其余用户必须是
 * 该课程的 owner（{@code teacher_id} 匹配）。配合各 Controller 端点内的
 * <b>资源归属校验</b>（pageId/scriptId/flowId/unitId 属于 courseId），构成双层防线：
 * <ol>
 *   <li>本拦截器：统一校验路径 courseId 归属 → 后续新增写端点零遗漏兜底</li>
 *   <li>Controller/Service：逐端点校验子资源归属 → 阻止"持自己课程 courseId + 他人资源 ID"
 *       的跨课程篡改 / 越权读取 / 消耗他人 TTS 额度</li>
 * </ol>
 *
 * <h3>异常处理</h3>
 * preHandle 抛出的 {@link BusinessException} 会经 DispatcherServlet 交给
 * {@link com.microcourse.plugin.interactive.exception.GlobalExceptionHandler}
 * 统一转为 R 响应（NO_PERMISSION → 403 / COURSE_NOT_FOUND → 404）。
 */
public class CourseAccessInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CourseAccessInterceptor.class);

    private static final String PPT_PATTERN = "/api/courses/{courseId}/ppt/**";
    private static final String HTML_PATTERN = "/api/courses/{courseId}/html/**";
    private static final String[] WRITE_METHODS = {"POST", "PUT", "DELETE", "PATCH"};

    private final CourseRepository courseRepository;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public CourseAccessInterceptor(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 HandlerMethod（静态资源 / 错误页等）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (!isWriteMethod(request.getMethod())) {
            return true;
        }
        Long courseId = extractCourseId(request.getRequestURI());
        if (courseId == null) {
            // 无法解析 courseId 的路径不应命中本拦截器模式；防御性放行，由 Controller 自行兜底
            return true;
        }
        verifyOwner(courseId);
        return true;
    }

    private boolean isWriteMethod(String method) {
        if (method == null) {
            return false;
        }
        for (String m : WRITE_METHODS) {
            if (m.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    private Long extractCourseId(String uri) {
        if (uri == null) {
            return null;
        }
        if (matcher.match(PPT_PATTERN, uri)) {
            return parseId(matcher.extractUriTemplateVariables(PPT_PATTERN, uri).get("courseId"));
        }
        if (matcher.match(HTML_PATTERN, uri)) {
            return parseId(matcher.extractUriTemplateVariables(HTML_PATTERN, uri).get("courseId"));
        }
        return null;
    }

    private Long parseId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void verifyOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            log.warn("[CourseAccessInterceptor] 越权写操作: courseId={}, userId={}, teacherId={}",
                    courseId, SecurityUtil.getCurrentUserIdOpt(), course.getTeacherId());
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程的课件");
        }
    }
}
