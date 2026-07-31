package com.microcourse.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;

import java.util.List;

/**
 * 课时课件查询 Service。
 * 处理课件页面列表查询，含 API Key / JWT 鉴权及课程 ownership 校验。
 */
public interface SectionSlideService {

    /**
     * 查询某课时的课件页面列表。
     *
     * @param courseId  课程 ID
     * @param sectionId 课时 ID
     * @param apiKey    可选的 API Key（Hermes 鉴权）
     * @return 课件页面列表
     */
    List<SlidePageVO> getSectionSlide(Long courseId, Long sectionId, String apiKey);
}
