package com.microcourse.service;

import com.microcourse.dto.BatchOperationResult;

import java.util.List;

/**
 * 教师自主删除课件服务 (sytafe 需求 2026-07-20).
 *
 * <p>范围: 课程(chapter / section) + 课件(slide PPT page / HTML unit) + 批量.</p>
 *
 * <p>核心约束:
 * <ul>
 *   <li>权限: TEACHER 仅可删自己名下 (courses.teacher_id = 当前用户) 的课件,
 *       ADMIN 可删任意 (复用 SecurityUtil.isOwnerOrAdmin).</li>
 *   <li>软删除: deleted_at = now() (MyBatis-Plus 逻辑删除), 保留审计痕迹.</li>
 *   <li>级联: 删 chapter → cascade 删 section → cascade 删 slide_ppt_pages / slide_html_units.</li>
 *   <li>IDOR 防御: 所有操作都校验 course_id 归属, 防止跨课程篡改 URL.</li>
 * </ul>
 * </p>
 *
 * <p>设计原因: 用户选择"保持软删除",避免物理删除带来的误删风险,
 * 后续可通过数据治理任务清理 deleted_at 非空的过期数据.</p>
 */
public interface CoursewareDeleteService {

    /**
     * 删除 chapter (级联删除其所有 section + 课件).
     *
     * @param courseId  课程 ID (URL 路径)
     * @param chapterId chapter ID
     * @return 删除统计
     */
    DeleteStats deleteChapter(Long courseId, Long chapterId);

    /**
     * 删除单个 section (级联删除其课件 PPT page / HTML unit).
     */
    DeleteStats deleteSection(Long courseId, Long sectionId);

    /**
     * 删除单个 PPT page.
     */
    DeleteStats deletePptPage(Long courseId, Long pptPageId);

    /**
     * 删除单个 HTML unit.
     */
    DeleteStats deleteHtmlUnit(Long courseId, Long htmlUnitId);

    /**
     * 批量删除 chapter 列表.
     */
    BatchOperationResult deleteChaptersBatch(Long courseId, List<Long> chapterIds);

    /**
     * 批量删除 PPT page 列表 (跨 section 允许).
     */
    BatchOperationResult deletePptPagesBatch(Long courseId, List<Long> pptPageIds);

    /**
     * 删除统计 (返回级联清理的条数).
     */
    record DeleteStats(
            int deletedChapters,
            int deletedSections,
            int deletedPptPages,
            int deletedHtmlUnits,
            int deletedPptScripts,
            int deletedHtmlSegmentScripts) {
    }
}