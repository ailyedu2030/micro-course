package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * slide_html_units mapper (V303).
 * uk_html_units_section UNIQUE (section_id).
 */
@Mapper
public interface SlideHtmlUnitMapper extends BaseMapper<SlideHtmlUnit> {

    @Select("SELECT * FROM slide_html_units WHERE section_id = #{sectionId} LIMIT 1")
    SlideHtmlUnit findBySection(@Param("sectionId") Long sectionId);

    /**
     * 按 chapter 查找 HTML 单元（章节级课件，section_id 为 NULL）。
     */
    @Select("SELECT * FROM slide_html_units WHERE chapter_id = #{chapterId} LIMIT 1")
    SlideHtmlUnit findByChapter(@Param("chapterId") Long chapterId);
}
