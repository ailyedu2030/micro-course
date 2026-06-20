package com.microcourse.service;

import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.dto.PageResult;

import java.util.List;

public interface CourseFavoriteService {

    void favorite(Long userId, Long courseId);

    void unfavorite(Long userId, Long courseId);

    List<CourseFavoriteVO> getMyFavorites(Long userId);

    /**
     * 分页查询所有收藏记录（管理员用）
     * @param page 页码（0-based）
     * @param size 每页条数
     * @param studentName 学员姓名（可选，模糊搜索）
     * @param courseName 课程名称（可选，模糊搜索）
     */
    PageResult<CourseFavoriteVO> listAll(int page, int size, String studentName, String courseName);

    boolean isFavorited(Long userId, Long courseId);
}
