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
 *
 * 【BUG #9 修复】 加 listByScriptIds 批量查询, 消除 CoursewareQueryService N+1.
 */
@Mapper
public interface SlidePptPageAudioMapper extends BaseMapper<SlidePptPageAudio> {

    @Select("SELECT * FROM slide_ppt_page_audios WHERE script_id = #{scriptId} "
          + "ORDER BY created_at DESC")
    List<SlidePptPageAudio> listByScript(@Param("scriptId") Long scriptId);

    /**
     * 按 token 查找 (流式 GET endpoint 用).
     * 不依赖 pageNumber, 与 7-19 P1-C 修复一致.
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE audio_token = #{token} LIMIT 1")
    SlidePptPageAudio findByToken(@Param("token") String token);

    /**
     * 【BUG #9 修复】 批量查询: 一次 SQL 取多个 script 的所有音频, 取代 N+1.
     * 配合 listByPageIds (script mapper) 实现 2 queries per tree.
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE script_id IN "
          + "(SELECT id FROM slide_ppt_page_scripts "
          + " WHERE ppt_page_id IN "
          + "   <foreach collection='pageIds' item='pid' open='(' separator=',' close=')'>"
          + "   #{pid}"
          + " </foreach>"
          + " AND is_active = TRUE) "
          + "ORDER BY created_at DESC")
    List<SlidePptPageAudio> listByPageIds(@Param("pageIds") List<Long> pageIds);
}