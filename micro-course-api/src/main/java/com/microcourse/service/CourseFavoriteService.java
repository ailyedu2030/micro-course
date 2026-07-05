package com.microcourse.service;

import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.dto.PageResult;

import java.util.List;
import java.util.Map;

public interface CourseFavoriteService {

    /**
     * 收藏课程。
     * @param userId 用户 ID
     * @param courseId 课程 ID
     * @return Map 包含 alreadyFavorited 标记: true=已收藏过, false=新收藏
     */
    Map<String, Object> favorite(Long userId, Long courseId);

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
