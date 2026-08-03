package com.microcourse.service;

/**
 * 课程复制 · 章节内容复制服务。
 *
 * <p>从 CourseAdminServiceImpl.copy() 中拆出（该文件超过 800 行预检限制），
 * 负责复制章节下的视频元数据 / 课时（sections）/ 练习（含题目关联）。
 * 调用方（CourseAdminServiceImpl.copy）处于 @Transactional 事务内，
 * 本服务方法默认 REQUIRED 传播，参与同一事务，任一失败整体回滚。</p>
 */
public interface CourseCopyContentService {

    /**
     * 复制指定章节下的全部内容到新章节。
     *
     * @param originalChapterId 源章节 ID
     * @param newChapterId      新章节 ID
     * @param newCourseId       新课程 ID
     */
    void copyChapterContent(Long originalChapterId, Long newChapterId, Long newCourseId);
}
