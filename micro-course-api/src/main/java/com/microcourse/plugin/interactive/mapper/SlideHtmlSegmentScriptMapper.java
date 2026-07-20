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
 *
 * 【BUG #9 修复】 加 listActiveByUnitIds 批量查询, 消除 HTML buildHtmlTree N+1.
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

    /**
     * 【BUG #9 修复】 批量取多个 unit 的 active segments (1 SQL, 取代 N 次).
     */
    @Select("SELECT * FROM slide_html_segment_scripts WHERE is_active = TRUE "
          + "AND html_unit_id IN "
          + "  <foreach collection='unitIds' item='uid' open='(' separator=',' close=')'>"
          + "  #{uid}"
          + "  </foreach> "
          + "ORDER BY html_unit_id, segment_index ASC")
    List<SlideHtmlSegmentScript> listActiveByUnitIds(@Param("unitIds") List<Long> unitIds);
}