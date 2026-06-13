package com.microcourse.service;

import com.microcourse.dto.BannerVO;

import java.util.List;

public interface BannerService {

    List<BannerVO> list();

    BannerVO create(String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled);

    BannerVO update(Long id, String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled);

    void delete(Long id);

    void toggleStatus(Long id, Boolean enabled);
}