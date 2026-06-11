package com.microcourse.service;

import com.microcourse.dto.CourseFavoriteVO;

import java.util.List;

public interface CourseFavoriteService {

    void favorite(Long userId, Long courseId);

    void unfavorite(Long userId, Long courseId);

    List<CourseFavoriteVO> getMyFavorites(Long userId);

    /**
     * 获取所有收藏记录（管理员用）
     * @return 所有收藏列表
     */
    List<CourseFavoriteVO> listAll();

    boolean isFavorited(Long userId, Long courseId);
}
