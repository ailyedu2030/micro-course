package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_ppt_pages 表 mapper (V300).
 *
 * 索引覆盖 (spec 6.1):
 * <ul>
 *   <li>idx_ppt_pages_section (section_id, page_number)</li>
 *   <li>idx_ppt_pages_course (course_id, section_id, page_number)</li>
 *   <li>uk_ppt_pages_slide_page (slide_id, page_number) UNIQUE</li>
 * </ul>
 */
@Mapper
public interface SlidePptPageMapper extends BaseMapper<SlidePptPage> {

    /**
     * 按 section 列出所有 PPT 页 (按 page_number 升序).
     * 使用 idx_ppt_pages_section.
     */
    @Select("SELECT * FROM slide_ppt_pages WHERE section_id = #{sectionId} "
          + "ORDER BY page_number ASC")
    List<SlidePptPage> listBySection(@Param("sectionId") Long sectionId);

    /**
     * 按 slide + page_number 查找 (唯一索引).
     */
    @Select("SELECT * FROM slide_ppt_pages WHERE slide_id = #{slideId} "
          + "AND page_number = #{pageNumber} LIMIT 1")
    SlidePptPage findBySlideAndPageNumber(@Param("slideId") Long slideId,
                                          @Param("pageNumber") Integer pageNumber);
}