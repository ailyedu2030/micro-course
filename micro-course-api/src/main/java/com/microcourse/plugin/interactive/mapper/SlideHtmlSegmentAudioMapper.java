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
 */
@Mapper
public interface SlideHtmlSegmentAudioMapper extends BaseMapper<SlideHtmlSegmentAudio> {

    @Select("SELECT * FROM slide_html_segment_audios WHERE segment_script_id = #{scriptId} "
          + "ORDER BY created_at DESC")
    List<SlideHtmlSegmentAudio> listByScript(@Param("scriptId") Long scriptId);

    @Select("SELECT * FROM slide_html_segment_audios WHERE audio_token = #{token} LIMIT 1")
    SlideHtmlSegmentAudio findByToken(@Param("token") String token);
}