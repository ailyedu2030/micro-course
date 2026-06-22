package com.microcourse.dto;

import java.time.LocalDateTime;

public class WrongQuestionVO {

    private Long id;
    private Long userId;
    private Long questionId;
    private String questionType;
    private String questionContent;
    private Long courseId;
    private Long chapterId;           // P0-3: 章节ID
    private String courseTitle;       // P0-3: 课程标题
    private String chapterTitle;      // P0-3: 章节标题
    private String correctAnswer;     // P0-3: 正确答案
    private String content;           // P0-3: 题目内容（冗余 questionContent，兼容前端）
    private Integer watchPosition;    // P0-3: 视频观看位置
    private Integer wrongCount;
    private LocalDateTime lastWrongAt;
    private LocalDateTime createdAt;

    public WrongQuestionVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getWatchPosition() { return watchPosition; }
    public void setWatchPosition(Integer watchPosition) { this.watchPosition = watchPosition; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public LocalDateTime getLastWrongAt() { return lastWrongAt; }
    public void setLastWrongAt(LocalDateTime lastWrongAt) { this.lastWrongAt = lastWrongAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}