package com.microcourse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 私有文件资源访问控制器（P0-SEC-001）。
 *
 * <p>处理 {@code /api/files/slides/**} 与 {@code /api/files/storage/**} 等私有类别文件的
 * 对象级授权下载，弥补 SecurityConfig 仅做路径级 {@code authenticated} 而无法进行 Owner
 * 校验的不足。</p>
 *
 * <h3>设计原理</h3>
 * <ol>
 *   <li><b>纵深防御</b>：SecurityConfig 提供第一道防线（{@code authenticated}），
 *       本 Controller 提供第二道防线（对象级 Owner 校验 + 选课校验）。</li>
 *   <li><b>公开/私有分离</b>：公开文件由 {@link com.microcourse.config.WebMvcConfig} 中的
 *       白名单静态映射直接服务，此类文件不经本 Controller，消除无谓的权限检查开销。</li>
 *   <li><b>路径穿越防护</b>：所有文件名先 URL 解码（防双编码绕过），再 normalize，
 *       最后验证在 uploads 根目录内。拒绝 {@code ..}、{@code /}、{@code \\} 字符。</li>
 *   <li><b>Content-Type 防浏览器 MIME 嗅探</b>：通过 {@code X-Content-Type-Options: nosniff}
 *       头禁止浏览器自动推断 Content-Type，降低 XSS 风险。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "FileAccessController", description = "私有文件资源访问（对象级授权下载）")
public class FileAccessController {

    private static final Logger log = LoggerFactory.getLogger(FileAccessController.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MicroSpecialtyProposalRepository proposalRepository;

    @Value("${upload.base-dir:uploads}")
    private String uploadBaseDir;

    public FileAccessController(CourseRepository courseRepository,
                                EnrollmentRepository enrollmentRepository,
                                MicroSpecialtyProposalRepository proposalRepository) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.proposalRepository = proposalRepository;
    }

    // ================================================================
    // 课件文件
    // ================================================================

    /**
     * GET /api/files/slides/{courseId}/{filename}
     *
     * <p>提供课件原始文件的授权下载。授权逻辑与 {@link SlideController#verifyAccess(Long)}
     * 一致：
     * <ul>
     *   <li>ADMIN/ACADEMIC：全部通行</li>
     *   <li>TEACHER：必须是课程的所有者（{@code course.teacherId == currentUserId}）</li>
     *   <li>STUDENT：必须已选此课并有 APPROVED/COMPLETED 的 enrollment 记录</li>
     * </ul>
     * </p>
     *
     * <p><b>迁移说明</b>：建议优先使用
     * {@code GET /api/courses/{courseId}/slides/download}（经 {@link SlideController}，
     * 功能更完整）。本端点仅作为兼容路径保留。</p>
     *
     * @param courseId 课程 ID
     * @param filename 文件名（如 {@code original.pptx}）
     * @return 文件资源（204 No Content 当文件不存在）
     */
    @GetMapping("/slides/{courseId}/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getSlideFile(@PathVariable Long courseId,
                                                  @PathVariable String filename) {
        // 1. 对象级授权：与 SlideController.verifyAccess() 一致
        verifySlideAccess(courseId);

        // 2. 路径穿越防护
        String decodedFilename = sanitizeFilename(filename);
        Path slidesBaseDir = Paths.get(uploadBaseDir, "slides", String.valueOf(courseId))
                .toAbsolutePath().normalize();
        Path filePath = slidesBaseDir.resolve(decodedFilename).normalize();
        if (!filePath.startsWith(slidesBaseDir)) {
            log.warn("[FileAccess] 路径穿越拦截 courseId={}, resolved={}", courseId, filePath);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法文件路径");
        }

        // 3. 检查文件是否存在
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.warn("[FileAccess] 课件文件不存在 courseId={}, path={}", courseId, filePath);
            return ResponseEntity.noContent().build();
        }

        // 4. 构造响应
        String contentType = resolveContentType(decodedFilename);
        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // ================================================================
    // 申报图片
    // ================================================================

    /**
     * GET /api/files/storage/{proposalId}/{filename}
     *
     * <p>提供微专业申报表签名/公章图片的授权访问。授权逻辑：
     * <ul>
     *   <li>ADMIN/ACADEMIC：全部通行</li>
     *   <li>TEACHER：必须是该申报表的提交者（{@code proposal.proposerId == currentUserId}）</li>
     * </ul>
     * </p>
     *
     * <p><b>迁移说明</b>：申报图片通常内嵌于 Word/PDF 导出文件中，通过
     * {@code GET /api/storage-applications/{id}/export-word} 或
     * {@code GET /api/storage-applications/{id}/export-pdf} 获取完整文档。
     * 本端点仅作为兼容路径保留。</p>
     *
     * @param proposalId 申报表 ID
     * @param filename 文件名（如 {@code signature_abc.jpg}）
     * @return 文件资源
     */
    @GetMapping("/storage/{proposalId}/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getStorageFile(@PathVariable Long proposalId,
                                                    @PathVariable String filename) {
        // 1. 对象级授权
        verifyStorageAccess(proposalId);

        // 2. 路径穿越防护
        String decodedFilename = sanitizeFilename(filename);
        Path storageBaseDir = Paths.get(uploadBaseDir, "storage", String.valueOf(proposalId))
                .toAbsolutePath().normalize();
        Path filePath = storageBaseDir.resolve(decodedFilename).normalize();
        if (!filePath.startsWith(storageBaseDir)) {
            log.warn("[FileAccess] 路径穿越拦截 proposalId={}, resolved={}", proposalId, filePath);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法文件路径");
        }

        // 3. 检查文件是否存在
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.warn("[FileAccess] 申报图片不存在 proposalId={}, path={}", proposalId, filePath);
            return ResponseEntity.noContent().build();
        }

        // 4. 构造响应
        String contentType = resolveContentType(decodedFilename);
        Resource resource = new FileSystemResource(filePath.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // ================================================================
    // 授权方法
    // ================================================================

    /**
     * 课件访问授权（与 {@link SlideController#verifyAccess(Long)} 一致）。
     * 身份叠加原则（权限矩阵 §4.1）：同时具备多个角色的用户，权限集合取并集。
     *
     * <h3>授权顺序（R3 显式化）</h3>
     * <ol>
     *   <li>ADMIN：全部通行</li>
     *   <li>ACADEMIC：全部通行（教务处有跨院查看权限）</li>
     *   <li>TEACHER：必须是课程所有者</li>
     *   <li>STUDENT：必须有 APPROVED/COMPLETED 选课记录</li>
     * </ol>
     */
    private void verifySlideAccess(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // 1) ADMIN 全部通行
        if (SecurityUtil.isAdmin()) {
            return;
        }
        // 2) ACADEMIC 全部通行（与 verifyStorageAccess 行为一致；之前隐式 fall-through 行为不变但显式化以防误改）
        if (SecurityUtil.hasRole("ACADEMIC")) {
            return;
        }
        Long currentUserId = SecurityUtil.getCurrentUserId();
        // 3) TEACHER 必须是课程所有者
        if (SecurityUtil.hasRole("TEACHER")) {
            if (!currentUserId.equals(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程");
            }
            return;
        }
        // 4) STUDENT 必须有选课记录
        if (SecurityUtil.hasRole("STUDENT")) {
            LambdaQueryWrapper<Enrollment> check = new LambdaQueryWrapper<>();
            check.eq(Enrollment::getUserId, currentUserId)
                    .eq(Enrollment::getCourseId, courseId)
                    .in(Enrollment::getEnrollmentStatus, "APPROVED", "COMPLETED")
                    .isNull(Enrollment::getDeletedAt);
            if (enrollmentRepository.selectCount(check) == 0) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "请先选课再查看课件");
            }
        }
    }

    /**
     * 申报图片访问授权。
     * ADMIN/ACADEMIC 全部通行；TEACHER 仅可访问自己的申报表。
     */
    private void verifyStorageAccess(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(SecurityUtil.getCurrentUserId())
                && !SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该申报文件");
        }
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 文件名安全净化：URL 解码 + 路径穿越字符检测。
     *
     * @param filename 原始文件名
     * @return 解码后的文件名
     * @throws BusinessException 含非法字符时抛出
     */
    private String sanitizeFilename(String filename) {
        String decoded = URLDecoder.decode(filename, StandardCharsets.UTF_8);
        if (decoded.contains("..") || decoded.contains("/") || decoded.contains("\\")
                || decoded.indexOf('\u0000') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法文件名");
        }
        return decoded;
    }

    /**
     * 根据文件扩展名推断 Content-Type。
     * 未知类型统一返回 {@code application/octet-stream} 防止浏览器 MIME 嗅探。
     */
    private String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lower.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF_VALUE;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        } else if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return MediaType.TEXT_HTML_VALUE + "; charset=utf-8";
        }
        // 未知类型：不暴露真实 MIME，防止浏览器自动执行
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
