package com.microcourse.service.impl;

import com.microcourse.dto.BannerVO;
import com.microcourse.entity.Banner;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.BannerRepository;
import com.microcourse.service.BannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BannerServiceImpl implements BannerService {

    private static final Logger log = LoggerFactory.getLogger(BannerServiceImpl.class);

    // P1I-007: Banner 外部链接域名白名单（仅允许跳转到可信域名）
    private static final List<String> ALLOWED_BANNER_DOMAINS = Arrays.asList(
            "ailyedu.cn",
            "www.ailyedu.cn",
            "microcourse.ailyedu.cn"
    );

    private final BannerRepository bannerRepository;

    public BannerServiceImpl(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    @Override
    @Cacheable(value = "banners", key = "'list'", sync = true)
    @Transactional(readOnly = true)
    public List<BannerVO> list() {
        List<Banner> banners = bannerRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Banner>()
                        .eq(Banner::getEnabled, true)
                        .orderByAsc(Banner::getSortOrder)
                        .last("LIMIT 50")
        );
        return banners.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerVO> listAll() {
        // R1-P1 修复:Admin 后台需要看全部 Banner(含禁用),不走缓存、不加 enabled 过滤
        List<Banner> banners = bannerRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Banner>()
                        .orderByAsc(Banner::getSortOrder)
                        .last("LIMIT 200")
        );
        return banners.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public BannerVO create(String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled, String title) {
        // P1I-007: 外部链接域名白名单校验
        validateLinkUrl(linkUrl);

        Banner banner = new Banner();
        banner.setImageUrl(imageUrl);
        banner.setLinkUrl(linkUrl);
        banner.setSortOrder(sortOrder);
        banner.setEnabled(enabled);
        banner.setTitle(title != null ? title : "");
        banner.setCreatedAt(LocalDateTime.now());
        banner.setUpdatedAt(LocalDateTime.now());
        bannerRepository.insert(banner);
        return convertToVO(banner);
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public BannerVO update(Long id, String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled) {
        Banner banner = bannerRepository.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        if (imageUrl != null) {
            banner.setImageUrl(imageUrl);
        }
        if (linkUrl != null) {
            // P1I-007: 外部链接域名白名单校验
            validateLinkUrl(linkUrl);
            banner.setLinkUrl(linkUrl);
        }
        if (sortOrder != null) {
            banner.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            banner.setEnabled(enabled);
        }
        banner.setUpdatedAt(LocalDateTime.now());
        bannerRepository.updateById(banner);
        return convertToVO(banner);
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Banner banner = bannerRepository.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }

        // P1I-054: 删除物理图片文件后再删除 DB 记录
        if (banner.getImageUrl() != null && !banner.getImageUrl().isBlank()) {
            try {
                // 从 imageUrl 提取文件名，构造完整路径
                String filename = banner.getImageUrl().substring(banner.getImageUrl().lastIndexOf('/') + 1);
                String uploadDir = System.getProperty("user.dir") + "/uploads/banners/";
                java.io.File file = new java.io.File(uploadDir + filename);
                if (file.exists()) {
                    file.delete();
                    log.info("[Banner] 物理图片文件已清理 path={}", file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("[Banner] 物理图片文件清理失败 url={}", banner.getImageUrl(), e);
            }
        }

        bannerRepository.deleteById(id);
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Boolean enabled) {
        Banner banner = bannerRepository.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        banner.setEnabled(enabled);
        banner.setUpdatedAt(LocalDateTime.now());
        bannerRepository.updateById(banner);
    }

    /**
     * 校验并保存 Banner 图片，返回图片 URL。
     * 包含大小校验、Content-Type 校验、魔数校验。
     * 由 AdminBannerController 原 saveBannerImage() 方法移入 Service 层。
     */
    @Override
    public String saveBannerImage(MultipartFile image) {
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片大小不能超过 5MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg")
                && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 jpg/png/webp 格式");
        }

        // SECURITY: 图片魔数校验（JPEG: FFD8FF, PNG: 89504E47, WebP: 52494646 + WEBP at offset 8）
        try (java.io.InputStream is = image.getInputStream()) {
            byte[] magic = new byte[12];
            int read = is.read(magic);
            if (read < 4) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小");
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF && (magic[1] & 0xFF) == 0xD8 && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89 && magic[1] == 'P' && magic[2] == 'N' && magic[3] == 'G';
            boolean isWebp = read >= 12 && magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F'
                    && magic[8] == 'W' && magic[9] == 'E' && magic[10] == 'B' && magic[11] == 'P';
            if (!isJpeg && !isPng && !isWebp) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片格式校验失败（仅支持JPEG/PNG/WebP）");
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取图片");
        }

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
            return imageUrl;
        } catch (Exception e) {
            log.error("[Banner] Banner图片上传失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片上传失败");
        }
    }

    /**
     * P1I-007: 校验 Banner 外部链接域名是否在白名单内。
     * 允许：null/空、相对路径（以 / 开头）、协议内链（//xxx.ailyedu.cn）、白名单域名。
     */
    private void validateLinkUrl(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return; // 空链接无需校验（纯图片 Banner）
        }
        // 相对路径（站内链接）直接放行
        if (linkUrl.startsWith("/")) {
            return;
        }
        // 协议相对路径（以 // 开头）
        if (linkUrl.startsWith("//")) {
            String host = linkUrl.substring(2).split("/")[0].toLowerCase();
            if (ALLOWED_BANNER_DOMAINS.stream().noneMatch(d -> host.equals(d) || host.endsWith("." + d))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "外部链接仅允许跳转到 *.ailyedu.cn 域名");
            }
            return;
        }
        // 完整 URL 校验
        try {
            java.net.URL url = new java.net.URL(linkUrl);
            String protocol = url.getProtocol();
            // 非 HTTP 协议拒绝（禁止 javascript: 等）
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "外部链接仅支持 http/https 协议");
            }
            String host = url.getHost().toLowerCase();
            // 校验域名是否在白名单内
            if (ALLOWED_BANNER_DOMAINS.stream().noneMatch(d -> host.equals(d) || host.endsWith("." + d))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "外部链接仅允许跳转到 *.ailyedu.cn 域名");
            }
        } catch (java.net.MalformedURLException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "外部链接格式不正确");
        }
    }

    private BannerVO convertToVO(Banner banner) {
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setTitle(banner.getTitle());
        vo.setImageUrl(banner.getImageUrl());
        vo.setLinkUrl(banner.getLinkUrl());
        vo.setSortOrder(banner.getSortOrder());
        vo.setEnabled(banner.getEnabled());
        vo.setCreatedAt(banner.getCreatedAt());
        vo.setUpdatedAt(banner.getUpdatedAt());
        return vo;
    }
}