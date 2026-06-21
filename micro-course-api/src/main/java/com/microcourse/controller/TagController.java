package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagUpdateRequest;
import com.microcourse.dto.TagVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.TagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<TagVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        PageResult<TagVO> result = tagService.page(page, size);
        return R.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<TagVO> create(@Valid @RequestBody TagCreateRequest request) {
        TagVO vo = tagService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<TagVO> update(@PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        TagVO vo = tagService.updateTag(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        tagService.deleteTag(id);
        return R.ok();
    }

    /**
     * GET /api/tags/course/{courseId}
     * 获取课程标签列表（Round 5-3 P1-10 新增）
     * 权限：已登录可读（依据 权限矩阵 v2.0 READ_COURSE_TAGS = 公开读）
     */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<TagVO>> courseTags(@PathVariable Long courseId) {
        return R.ok(tagService.getCourseTags(courseId));
    }

    /**
     * POST /api/tags/course/{courseId}
     * 为课程添加标签（Round 5-3 P1-10 新增）
     * 权限：TEACHER(课程创建者) / ADMIN（owner 校验下沉 Service）
     * @param body {"tagId": 123}
     */
    @PostMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> addCourseTag(@PathVariable Long courseId, @RequestBody Map<String, Long> body) {
        Long tagId = body != null ? body.get("tagId") : null;
        if (tagId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "tagId 不能为空");
        }
        tagService.addCourseTag(courseId, tagId);
        return R.ok();
    }

    /**
     * DELETE /api/tags/course/{courseId}/{tagId}
     * 移除课程标签（Round 5-3 P1-10 新增）
     * 权限：TEACHER(课程创建者) / ADMIN（owner 校验下沉 Service）
     */
    @DeleteMapping("/course/{courseId}/{tagId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> removeCourseTag(@PathVariable Long courseId, @PathVariable Long tagId) {
        tagService.removeCourseTag(courseId, tagId);
        return R.ok();
    }
}