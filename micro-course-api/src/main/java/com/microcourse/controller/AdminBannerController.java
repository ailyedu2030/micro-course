package com.microcourse.controller;

import com.microcourse.dto.BannerToggleStatusRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/banners")
public class AdminBannerController {

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
        if (image.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片不能为空");
        }
        // SEC-007 修复: 文件名路径穿越校验
        com.microcourse.util.FileUploadUtil.assertSafeFilename(image.getOriginalFilename());
        String imageUrl = bannerService.saveBannerImage(image);
        BannerVO banner = bannerService.create(imageUrl, linkUrl, sortOrder, enabled);
        return R.ok(banner);
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
            com.microcourse.util.FileUploadUtil.assertSafeFilename(image.getOriginalFilename());
            imageUrl = bannerService.saveBannerImage(image);
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
    public R<Void> toggleStatus(@PathVariable Long id, @Valid @RequestBody BannerToggleStatusRequest request) {
        bannerService.toggleStatus(id, request.getEnabled());
        return R.ok();
    }
}