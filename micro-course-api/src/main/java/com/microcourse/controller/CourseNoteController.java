package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.CourseNote;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseNoteRepository;
import com.microcourse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习笔记接口（P1-C 修复 2026-08-04）。
 *
 * <p>course_notes 表/实体/仓库早已存在，但一直无接口、前端"笔记"按钮无任何功能。
 * 本 Controller 提供登录用户自己笔记的增删查（最小闭环）。</p>
 */
@RestController
@RequestMapping("/api/course-notes")
@PreAuthorize("isAuthenticated()")
public class CourseNoteController {

    private final CourseNoteRepository noteRepository;

    public CourseNoteController(CourseNoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /** GET /api/course-notes?courseId=&chapterId=&videoId= 查询当前用户笔记 */
    @GetMapping
    public R<List<CourseNote>> list(@RequestParam(required = false) Long courseId,
                                    @RequestParam(required = false) Long chapterId,
                                    @RequestParam(required = false) Long videoId) {
        Long userId = SecurityUtil.getCurrentUserId();
        LambdaQueryWrapper<CourseNote> wrapper = new LambdaQueryWrapper<CourseNote>()
                .eq(CourseNote::getUserId, userId)
                .eq(courseId != null, CourseNote::getCourseId, courseId)
                .eq(chapterId != null, CourseNote::getChapterId, chapterId)
                .eq(videoId != null, CourseNote::getVideoId, videoId)
                .orderByDesc(CourseNote::getCreatedAt)
                .last("LIMIT 100");
        return R.ok(noteRepository.selectList(wrapper));
    }

    /** POST /api/course-notes 创建笔记 */
    @PostMapping
    public R<CourseNote> create(@Valid @RequestBody CourseNote note) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (note.getCourseId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程ID不能为空");
        }
        if (note.getContent() == null || note.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "笔记内容不能为空");
        }
        // 唯一约束 idx_cn_unique(user_id, course_id, chapter_id, video_id)：
        // 同一用户同一章节（同一视频）仅一条笔记 → upsert 语义（存在则更新）。
        CourseNote existing = noteRepository.selectOne(
                new LambdaQueryWrapper<CourseNote>()
                        .eq(CourseNote::getUserId, userId)
                        .eq(CourseNote::getCourseId, note.getCourseId())
                        .eq(note.getChapterId() != null, CourseNote::getChapterId, note.getChapterId())
                        .isNull(note.getChapterId() == null, CourseNote::getChapterId)
                        .eq(note.getVideoId() != null, CourseNote::getVideoId, note.getVideoId())
                        .isNull(note.getVideoId() == null, CourseNote::getVideoId)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setContent(note.getContent());
            if (note.getTitle() != null && !note.getTitle().isBlank()) {
                existing.setTitle(note.getTitle());
            } else {
                String content = note.getContent().trim();
                existing.setTitle(content.length() > 30 ? content.substring(0, 30) : content);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            noteRepository.updateById(existing);
            return R.ok(existing);
        }
        note.setId(null);
        note.setUserId(userId);
        // course_notes.title NOT NULL：未提供时取内容前 30 字作为默认标题
        if (note.getTitle() == null || note.getTitle().isBlank()) {
            String content = note.getContent().trim();
            note.setTitle(content.length() > 30 ? content.substring(0, 30) : content);
        }
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteRepository.insert(note);
        return R.ok(note);
    }

    /** DELETE /api/course-notes/{id} 删除自己的笔记 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        CourseNote note = noteRepository.selectById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "笔记不存在");
        }
        if (!userId.equals(note.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        noteRepository.deleteById(id);
        return R.ok();
    }
}
