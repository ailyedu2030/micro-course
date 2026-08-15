package com.microcourse.controller;

import com.microcourse.dto.CourseNoteCreateRequest;
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
    public R<CourseNote> create(@Valid @RequestBody CourseNoteCreateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        CourseNote existing = noteRepository.selectOne(
                new LambdaQueryWrapper<CourseNote>()
                        .eq(CourseNote::getUserId, userId)
                        .eq(CourseNote::getCourseId, req.getCourseId())
                        .eq(req.getChapterId() != null, CourseNote::getChapterId, req.getChapterId())
                        .isNull(req.getChapterId() == null, CourseNote::getChapterId)
                        .eq(req.getVideoId() != null, CourseNote::getVideoId, req.getVideoId())
                        .isNull(req.getVideoId() == null, CourseNote::getVideoId)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setContent(req.getContent());
            if (req.getTitle() != null && !req.getTitle().isBlank()) {
                existing.setTitle(req.getTitle());
            } else {
                String content = req.getContent().trim();
                existing.setTitle(content.length() > 30 ? content.substring(0, 30) : content);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            noteRepository.updateById(existing);
            return R.ok(existing);
        }
        CourseNote note = new CourseNote();
        note.setUserId(userId);
        note.setCourseId(req.getCourseId());
        note.setChapterId(req.getChapterId());
        note.setVideoId(req.getVideoId());
        note.setVideoPosition(req.getVideoPosition());
        note.setContent(req.getContent());
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            note.setTitle(req.getTitle());
        } else {
            String content = req.getContent().trim();
            note.setTitle(content.length() > 30 ? content.substring(0, 30) : content);
        }
        note.setIsPublic(req.getIsPublic() != null ? req.getIsPublic() : false);
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
