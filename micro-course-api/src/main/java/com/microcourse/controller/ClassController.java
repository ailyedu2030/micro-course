package com.microcourse.controller;

import com.microcourse.dto.ClassCreateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.microcourse.dto.ClassStudentVO;
import com.microcourse.dto.ClassUpdateRequest;
import com.microcourse.dto.ClassVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.ClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@Tag(name = "班级管理", description = "班级管理 API")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<ClassVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        PageResult<ClassVO> result = classService.page(page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<ClassVO> getById(@PathVariable Long id) {
        ClassVO vo = classService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<ClassVO> create(@Valid @RequestBody ClassCreateRequest request) {
        ClassVO vo = classService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<ClassVO> update(@PathVariable Long id,
                             @Valid @RequestBody ClassUpdateRequest request) {
        ClassVO vo = classService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        classService.delete(id);
        return R.ok();
    }

    /**
     * GET /api/classes/{id}/students
     * 获取班级学生名单（Round 5-3 P1-10 新增）
     * 权限：TEACHER(辅导员) / ADMIN / ACADEMIC（依据 权限矩阵 v2.0 READ_CLASS_STUDENTS）
     *
     * <p>角色级 @PreAuthorize 收紧至 T/A/AC；班级不存在返回 404。学生名单源自 users 表（classId 关联），
     * 不涉及对象级写操作，沿用既有 GET 读语义，合法用户操作零感。</p>
     */
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<ClassStudentVO>> students(@PathVariable Long id) {
        return R.ok(classService.getStudents(id));
    }
}
