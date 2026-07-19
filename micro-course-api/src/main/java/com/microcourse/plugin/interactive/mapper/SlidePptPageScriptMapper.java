package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_ppt_page_scripts mapper (V301).
 * Partial unique: uk_ppt_scripts_active (ppt_page_id) WHERE is_active = TRUE.
 *
 * 【BUG #9 修复】 加 listActiveByPageIds 批量查询, 消除 CoursewareQueryService N+1.
 */
@Mapper
public interface SlidePptPageScriptMapper extends BaseMapper<SlidePptPageScript> {

    @Select("SELECT * FROM slide_ppt_page_scripts WHERE ppt_page_id = #{pptPageId} "
          + "AND is_active = TRUE LIMIT 1")
    SlidePptPageScript findActiveByPage(@Param("pptPageId") Long pptPageId);

    @Select("SELECT * FROM slide_ppt_page_scripts WHERE ppt_page_id = #{pptPageId} "
          + "ORDER BY script_version DESC")
    List<SlidePptPageScript> listHistoryByPage(@Param("pptPageId") Long pptPageId);

    /**
     * 【BUG #9 修复】 批量取多个 PPT page 的 active script (1 SQL, 取代 N 次).
     * 用 IN + LIMIT 防误用 (100 个 page 一次查).
     */
    @Select("SELECT * FROM slide_ppt_page_scripts WHERE is_active = TRUE "
          + "AND ppt_page_id IN "
          + "  <foreach collection='pageIds' item='pid' open='(' separator=',' close=')'>"
          + "  #{pid}"
          + "  </foreach>")
    List<SlidePptPageScript> listActiveByPageIds(@Param("pageIds") List<Long> pageIds);
}