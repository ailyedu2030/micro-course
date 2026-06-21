package com.microcourse.controller;

import com.microcourse.dto.BannerVO;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.BannerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/banners")
public class AdminBannerController {

    private static final Logger log = LoggerFactory.getLogger(AdminBannerController.class);

    private final BannerService bannerService;

    public AdminBannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC')")
    public R<List<BannerVO>> list() {
        // R1-P1 修复:Admin 后台需要看全部(包括禁用),不走前台缓存的 list()
        return R.ok(bannerService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<BannerVO> create(@RequestParam MultipartFile image,
                              @RequestParam String linkUrl,
                              @RequestParam(defaultValue = "0") Integer sortOrder,
                              @RequestParam(defaultValue = "true") Boolean enabled) {
        // 1. 校验图片
        if (image.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片不能为空");
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片大小不能超过 5MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg")
                && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 jpg/png/webp 格式");
        }

        // SECURITY: 图片魔数校验（JPEG: FFD8FF, PNG: 89504E47, WebP: 52494646）
        try (java.io.InputStream is = image.getInputStream()) {
            byte[] magic = new byte[8];
            int read = is.read(magic);
            if (read < 4) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小");
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF && (magic[1] & 0xFF) == 0xD8 && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89 && magic[1] == 'P' && magic[2] == 'N' && magic[3] == 'G';
            if (!isJpeg && !isPng) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片格式校验失败（仅支持JPEG/PNG）");
        } catch (java.io.IOException e) { throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取图片"); }

        // 2. 保存图片到 uploads/banners/{uuid}.{ext}
        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/banners/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String ext = contentType.equals("image/jpeg") ? "jpg"
                    : contentType.equals("image/png") ? "png" : "webp";
            String filename = UUID.randomUUID().toString() + "." + ext;
            java.io.File dest = new java.io.File(uploadDir + filename);
            image.transferTo(dest);

            String imageUrl = "/api/files/banners/" + filename;

            // 3. 创建 Banner 记录
            BannerVO banner = bannerService.create(imageUrl, linkUrl, sortOrder, enabled);
            return R.ok(banner);
        } catch (Exception e) {
            log.error("[Banner] 创建Banner图片上传失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片上传失败");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<BannerVO> update(@PathVariable Long id,
                              @RequestParam(required = false) MultipartFile image,
                              @RequestParam(required = false) String linkUrl,
                              @RequestParam(required = false) Integer sortOrder,
                              @RequestParam(required = false) Boolean enabled) {
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            // 校验图片
            if (image.getSize() > 5 * 1024 * 1024) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片大小不能超过 5MB");
            }
            String contentType = image.getContentType();
            if (contentType == null || (!contentType.equals("image/jpeg")
                    && !contentType.equals("image/png")
                    && !contentType.equals("image/webp"))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 jpg/png/webp 格式");
            }

            // 保存新图片
            try {
                String uploadDir = System.getProperty("user.dir") + "/uploads/banners/";
                java.io.File dir = new java.io.File(uploadDir);
                if (!dir.exists()) dir.mkdirs();

                String ext = contentType.equals("image/jpeg") ? "jpg"
                        : contentType.equals("image/png") ? "png" : "webp";
                String filename = UUID.randomUUID().toString() + "." + ext;
                java.io.File dest = new java.io.File(uploadDir + filename);
                image.transferTo(dest);

                imageUrl = "/banners/" + filename;
            } catch (Exception e) {
                log.error("[Banner] 更新Banner图片上传失败 id={}", id, e);
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片上传失败");
            }
        }

        BannerVO banner = bannerService.update(id, imageUrl, linkUrl, sortOrder, enabled);
        return R.ok(banner);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC')")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        bannerService.toggleStatus(id, enabled);
        return R.ok();
    }
}