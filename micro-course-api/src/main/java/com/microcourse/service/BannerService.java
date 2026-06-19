package com.microcourse.service;

import com.microcourse.dto.BannerVO;

import java.util.List;

public interface BannerService {

    List<BannerVO> list();

    /**
     * 管理后台:列出全部 Banner(含禁用),不走缓存
     */
    List<BannerVO> listAll();

    BannerVO create(String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled);

    BannerVO update(Long id, String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled);

    void delete(Long id);

    void toggleStatus(Long id, Boolean enabled);
}