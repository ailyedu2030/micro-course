package com.microcourse.dto.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 章节-教师分配项 (Phase 2)
 * 用于保存和加载 assign 记录。
 */
public class ChapterAssignmentItem {

    private Long id;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotNull(message = "章节ID不能为空")
    @Min(value = 1, message = "章节ID不能为0")
    private Long chapterId;

    @NotNull(message = "教师ID不能为空")
    @Min(value = 1, message = "教师ID不能为0")
    private Long teacherId;

    /** 章节标题 (加载时填充, 便于展示) */
    private String chapterTitle;

    /** 课程名称 (加载时填充, 便于展示) */
    private String courseTitle;

    /** 来源 (保存时写 TBD, 加载时回读) */
    private String source;

    /** 接受状态 (默认 PENDING) */
    private String acceptStatus;

    public ChapterAssignmentItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getAcceptStatus() { return acceptStatus; }
    public void setAcceptStatus(String acceptStatus) { this.acceptStatus = acceptStatus; }
}
