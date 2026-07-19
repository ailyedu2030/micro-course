package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_ppt_page_audios mapper (V302).
 * audio_token 是部分索引 (WHERE audio_token IS NOT NULL), 用于 7-19 P1-C 兼容的流式 GET.
 */
@Mapper
public interface SlidePptPageAudioMapper extends BaseMapper<SlidePptPageAudio> {

    @Select("SELECT * FROM slide_ppt_page_audios WHERE script_id = #{scriptId} "
          + "ORDER BY created_at DESC")
    List<SlidePptPageAudio> listByScript(@Param("scriptId") Long scriptId);

    /**
     * 按 audio_token 查找 (流式 GET endpoint 用).
     * 不依赖 pageNumber, 与 7-19 P1-C 修复一致.
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE audio_token = #{token} LIMIT 1")
    SlidePptPageAudio findByToken(@Param("token") String token);
}