package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_ppt_pages 表 mapper (V300).
 *
 * 索引覆盖 (spec 6.1):
 * <ul>
 *   <li>idx_ppt_pages_section (section_id, page_number)</li>
 *   <li>idx_ppt_pages_course (course_id, section_id, page_number)</li>
 *   <li>uk_ppt_pages_slide_page (slide_id, page_number) UNIQUE</li>
 * </ul>
 */
@Mapper
public interface SlidePptPageMapper extends BaseMapper<SlidePptPage> {

    /**
     * 按 section 列出所有 PPT 页 (按 page_number 升序).
     * 使用 idx_ppt_pages_section.
     */
    @Select("SELECT * FROM slide_ppt_pages WHERE section_id = #{sectionId} "
          + "ORDER BY page_number ASC")
    List<SlidePptPage> listBySection(@Param("sectionId") Long sectionId);

    /**
     * 按 chapter 列出所有 PPT 页（章节级课件；slide_ppt_pages.section_id 指向"PPT 课件节"锚点 section，
     * 读取侧按 chapter_id 检索，不依赖 section 归属）。
     */
    @Select("SELECT * FROM slide_ppt_pages WHERE chapter_id = #{chapterId} "
          + "ORDER BY page_number ASC")
    List<SlidePptPage> listByChapter(@Param("chapterId") Long chapterId);

    /**
     * 按 slide + page_number 查找 (唯一索引).
     */
    @Select("SELECT * FROM slide_ppt_pages WHERE slide_id = #{slideId} "
          + "AND page_number = #{pageNumber} LIMIT 1")
    SlidePptPage findBySlideAndPageNumber(@Param("slideId") Long slideId,
                                          @Param("pageNumber") Integer pageNumber);

    /**
     * P0-B (I2): 按 course 列出全部 v2 PPT 页（学生端无参数入口 course 级兜底）。
     * 按 section_id, chapter_id, page_number 排序，保证同 section 页面连续且顺序稳定。
     */
    @Select("SELECT * FROM slide_ppt_pages WHERE course_id = #{courseId} "
          + "ORDER BY section_id, chapter_id, page_number ASC")
    List<SlidePptPage> listByCourse(@Param("courseId") Long courseId);
}
