package com.microcourse.service;

import com.microcourse.dto.BannerVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BannerService {

    List<BannerVO> list();

    /**
     * 管理后台:列出全部 Banner(含禁用),不走缓存
     */
    List<BannerVO> listAll();

    /**
     * 校验并保存 Banner 图片，返回图片 URL（含大小校验、Content-Type 校验、魔数校验）。
     */
    String saveBannerImage(MultipartFile file);

    BannerVO create(String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled, String title);

    BannerVO update(Long id, String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled);

    void delete(Long id);

    void toggleStatus(Long id, Boolean enabled);
}