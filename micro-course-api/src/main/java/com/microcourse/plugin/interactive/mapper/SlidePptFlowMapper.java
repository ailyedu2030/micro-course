package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_ppt_flow mapper (V306).
 * 索引 idx_ppt_flow_section_from (section_id, from_page_id, priority).
 */
@Mapper
public interface SlidePptFlowMapper extends BaseMapper<SlidePptFlow> {

    @Select("SELECT * FROM slide_ppt_flow WHERE section_id = #{sectionId} "
          + "AND from_page_id = #{fromPageId} ORDER BY priority ASC")
    List<SlidePptFlow> listBySectionAndFromPage(@Param("sectionId") Long sectionId,
                                                 @Param("fromPageId") Long fromPageId);

    @Select("SELECT * FROM slide_ppt_flow WHERE section_id = #{sectionId} "
          + "ORDER BY from_page_id, priority ASC")
    List<SlidePptFlow> listBySection(@Param("sectionId") Long sectionId);
}