package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_html_segment_audios mapper (V305).
 * audio_token 部分索引, 流式 GET endpoint 用.
 *
 * 【BUG #9 修复】 加 listByUnitIds 批量查询, 消除 HTML buildHtmlTree N+1.
 */
@Mapper
public interface SlideHtmlSegmentAudioMapper extends BaseMapper<SlideHtmlSegmentAudio> {

    @Select("SELECT * FROM slide_html_segment_audios WHERE segment_script_id = #{scriptId} "
          + "ORDER BY created_at DESC")
    List<SlideHtmlSegmentAudio> listByScript(@Param("scriptId") Long scriptId);

    @Select("SELECT * FROM slide_html_segment_audios WHERE audio_token = #{token} LIMIT 1")
    SlideHtmlSegmentAudio findByToken(@Param("token") String token);

    /**
     * 【BUG #9 修复】 批量取多个 unit 的所有音频 (1 SQL).
     */
    @Select("SELECT * FROM slide_html_segment_audios WHERE segment_script_id IN "
          + "(SELECT id FROM slide_html_segment_scripts "
          + " WHERE is_active = TRUE "
          + "   AND html_unit_id IN "
          + "     <foreach collection='unitIds' item='uid' open='(' separator=',' close=')'>"
          + "     #{uid}"
          + "     </foreach>")
    List<SlideHtmlSegmentAudio> listByUnitIds(@Param("unitIds") List<Long> unitIds);
}