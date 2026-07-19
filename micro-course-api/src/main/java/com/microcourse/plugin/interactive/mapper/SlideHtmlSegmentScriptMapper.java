package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_html_segment_scripts mapper (V304).
 * Partial unique: uk_html_seg_scripts_active (html_unit_id, segment_index) WHERE is_active = TRUE.
 */
@Mapper
public interface SlideHtmlSegmentScriptMapper extends BaseMapper<SlideHtmlSegmentScript> {

    @Select("SELECT * FROM slide_html_segment_scripts WHERE html_unit_id = #{unitId} "
          + "AND is_active = TRUE ORDER BY segment_index ASC")
    List<SlideHtmlSegmentScript> listActiveByUnit(@Param("unitId") Long unitId);

    @Select("SELECT * FROM slide_html_segment_scripts WHERE html_unit_id = #{unitId} "
          + "AND segment_index = #{idx} AND is_active = TRUE LIMIT 1")
    SlideHtmlSegmentScript findActiveByUnitAndIndex(@Param("unitId") Long unitId,
                                                     @Param("idx") Integer idx);
}