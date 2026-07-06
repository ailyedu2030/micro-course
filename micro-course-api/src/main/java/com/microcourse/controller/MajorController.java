package com.microcourse.controller;

import com.microcourse.dto.ClassVO;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.microcourse.dto.MajorCreateRequest;
import com.microcourse.dto.MajorUpdateRequest;
import com.microcourse.dto.MajorVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.ClassService;
import com.microcourse.service.MajorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/majors")
@Tag(name = "专业管理", description = "专业管理 API")
public class MajorController {

    private final MajorService majorService;
    private final ClassService classService;

    public MajorController(MajorService majorService, ClassService classService) {
        this.majorService = majorService;
        this.classService = classService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<MajorVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        PageResult<MajorVO> result = majorService.page(page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<MajorVO> getById(@PathVariable Long id) {
        MajorVO vo = majorService.getById(id);
        return R.ok(vo);
    }

    /**
     * GET /api/majors/{id}/classes
     * 【P1-C 修复】按专业查询班级列表 (权限矩阵 v4.1)
     * 权限: STUDENT / TEACHER / ADMIN / ACADEMIC
     */
    @GetMapping("/{id}/classes")
    @PreAuthorize("isAuthenticated()")
    public R<List<ClassVO>> getClassesByMajor(@PathVariable Long id) {
        return R.ok(classService.listByMajorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<MajorVO> create(@Valid @RequestBody MajorCreateRequest request) {
        MajorVO vo = majorService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<MajorVO> update(@PathVariable Long id,
                             @Valid @RequestBody MajorUpdateRequest request) {
        MajorVO vo = majorService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        majorService.delete(id);
        return R.ok();
    }
}