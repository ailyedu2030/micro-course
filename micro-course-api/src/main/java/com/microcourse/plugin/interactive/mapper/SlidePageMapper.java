package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SlidePageMapper extends BaseMapper<SlidePage> {

    /**
     * 批量插入 SlidePage (foreach 模式,一条 SQL 插入多行)
     */
    @Insert({
        "<script>",
        "INSERT INTO slide_pages (slide_id, course_id, page_number, file_uuid, image_url, thumbnail_url, ",
        "image_width, image_height, extracted_text, has_animation, has_embedded_media, ",
        "narration_status, created_at, updated_at) VALUES ",
        "<foreach collection='list' item='p' separator=','>",
        "(#{p.slideId}, #{p.courseId}, #{p.pageNumber}, #{p.fileUuid}, #{p.imageUrl}, #{p.thumbnailUrl}, ",
        "#{p.imageWidth}, #{p.imageHeight}, #{p.extractedText}, #{p.hasAnimation}, #{p.hasEmbeddedMedia}, ",
        "#{p.narrationStatus}, #{p.createdAt}, #{p.updatedAt})",
        "</foreach>",
        "</script>"
    })
    int insertBatch(@Param("list") List<SlidePage> list);
}
