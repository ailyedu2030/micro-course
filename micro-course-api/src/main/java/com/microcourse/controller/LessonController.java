package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * @deprecated lessons 表已删除（V186），课时数据迁移到 course_sections。
 * 请使用 SectionController: /api/courses/{courseId}/chapters/{chapterId}/sections
 */
@Deprecated
@RestController
@RequestMapping("/api/lessons")
@Tag(name = "LessonController (已废弃)")
public class LessonController {

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> create() {
        throw new BusinessException(ErrorCode.SECTION_NOT_FOUND, "lessons 表已废弃，请使用 Section API");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> update() {
        throw new BusinessException(ErrorCode.SECTION_NOT_FOUND, "lessons 表已废弃，请使用 Section API");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete() {
        throw new BusinessException(ErrorCode.SECTION_NOT_FOUND, "lessons 表已废弃，请使用 Section API");
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> sort() {
        throw new BusinessException(ErrorCode.SECTION_NOT_FOUND, "lessons 表已废弃，请使用 Section API");
    }

    @GetMapping("/chapter/{chapterId}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> getByChapter() {
        throw new BusinessException(ErrorCode.SECTION_NOT_FOUND, "lessons 表已废弃，请使用 Section API");
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> getById() {
        throw new BusinessException(ErrorCode.SECTION_NOT_FOUND, "lessons 表已废弃，请使用 Section API");
    }
}
