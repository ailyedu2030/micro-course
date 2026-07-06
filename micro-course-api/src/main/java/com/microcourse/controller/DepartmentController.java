package com.microcourse.controller;

import com.microcourse.dto.DepartmentCreateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.microcourse.dto.DepartmentStatsVO;
import com.microcourse.dto.DepartmentUpdateRequest;
import com.microcourse.dto.DepartmentVO;
import com.microcourse.dto.MajorVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.DepartmentService;
import com.microcourse.service.MajorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "院系管理", description = "院系管理 API")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final MajorService majorService;

    public DepartmentController(DepartmentService departmentService, MajorService majorService) {
        this.departmentService = departmentService;
        this.majorService = majorService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<DepartmentVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        PageResult<DepartmentVO> result = departmentService.page(page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<DepartmentVO> getById(@PathVariable Long id) {
        DepartmentVO vo = departmentService.getById(id);
        return R.ok(vo);
    }

    /**
     * GET /api/departments/{id}/majors
     * 【P1-C 修复】按院系查询专业列表 (权限矩阵 v4.1)
     * 权限: STUDENT / TEACHER / ADMIN / ACADEMIC
     */
    @GetMapping("/{id}/majors")
    @PreAuthorize("isAuthenticated()")
    public R<List<MajorVO>> getMajorsByDepartment(@PathVariable Long id) {
        return R.ok(majorService.listByDepartmentId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<DepartmentVO> create(@Valid @RequestBody DepartmentCreateRequest request) {
        DepartmentVO vo = departmentService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<DepartmentVO> update(@PathVariable Long id,
                                  @Valid @RequestBody DepartmentUpdateRequest request) {
        DepartmentVO vo = departmentService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return R.ok();
    }

    /**
     * GET /api/departments/{id}/stats
     * 获取院系统计数据（开课数 / 学生数 / 选课数）（Round 5-3 P1-10 新增）
     * 权限：ADMIN / ACADEMIC（依据 权限矩阵 v2.0 READ_DEPARTMENT_STATS）
     *
     * <p>角色级 @PreAuthorize 收紧至 A/AC；院系不存在返回 404。纯读端点，合法用户操作零感。</p>
     */
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<DepartmentStatsVO> stats(@PathVariable Long id) {
        return R.ok(departmentService.computeStats(id));
    }
}