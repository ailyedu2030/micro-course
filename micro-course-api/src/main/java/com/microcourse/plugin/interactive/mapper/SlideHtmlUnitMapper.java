package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * P0-B (I2): 按 course 列出全部 v2 HTML 单元（学生端无参数入口 course 级兜底）。
     * 按 section_id, chapter_id 排序，保证选择稳定。
     */
    @Select("SELECT * FROM slide_html_units WHERE course_id = #{courseId} "
          + "ORDER BY section_id, chapter_id ASC")
    List<SlideHtmlUnit> listByCourse(@Param("courseId") Long courseId);
}
