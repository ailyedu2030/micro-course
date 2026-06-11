package com.microcourse.service;

import com.microcourse.dto.CourseFavoriteVO;

import java.util.List;

public interface CourseFavoriteService {

    void favorite(Long userId, Long courseId);

    void unfavorite(Long userId, Long courseId);

    List<CourseFavoriteVO> getMyFavorites(Long userId);

    boolean isFavorited(Long userId, Long courseId);
}
