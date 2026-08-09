package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * slide_html_units mapper (V303).
 * uk_html_units_section UNIQUE (section_id).
 */
@Mapper
public interface SlideHtmlUnitMapper extends BaseMapper<SlideHtmlUnit> {

    @Select("SELECT * FROM slide_html_units WHERE section_id = #{sectionId} LIMIT 1")
    SlideHtmlUnit findBySection(@Param("sectionId") Long sectionId);

    /**
     * 按 chapter 查找 HTML 单元 —— 仅匹配【章节级】单元（section_id IS NULL）。
     *
     * D-5 R3（P1-C 数据损坏防御）：此前 SQL 只 WHERE chapter_id=? LIMIT 1，
     * 会命中该章节下【课时级】unit（如 chapter 222 下 section 86 的 unit），
     * 导致"仅 chapterId 上传"用章节级文件覆盖课时级内容。
     * section_id IS NULL 约束保证本方法永不返回课时级 unit，
     * 章节级上传/读取不会误覆盖课时级课件（读侧兜底见 listFirstByChapter）。
     */
    @Select("SELECT * FROM slide_html_units WHERE chapter_id = #{chapterId} "
          + "AND section_id IS NULL LIMIT 1")
    SlideHtmlUnit findByChapter(@Param("chapterId") Long chapterId);

    /**
     * D-5 R3 读侧兜底：按 chapter 返回【任意】unit（含课时级，按 section_id 最小优先）。
     * 学生端章节级入口（无 sectionId）需要看到该章节已有内容时使用本方法，
     * 与旧 findByChapter 语义一致（章节回退 = 章节内第一个 unit）。
     */
    @Select("SELECT * FROM slide_html_units WHERE chapter_id = #{chapterId} "
          + "ORDER BY section_id ASC NULLS FIRST, id ASC LIMIT 1")
    SlideHtmlUnit listFirstByChapter(@Param("chapterId") Long chapterId);

    /**
     * P0-B (I2): 按 course 列出全部 v2 HTML 单元（学生端无参数入口 course 级兜底）。
     * 按 section_id, chapter_id 排序，保证选择稳定。
     */
    @Select("SELECT * FROM slide_html_units WHERE course_id = #{courseId} "
          + "ORDER BY section_id, chapter_id ASC")
    List<SlideHtmlUnit> listByCourse(@Param("courseId") Long courseId);
}
