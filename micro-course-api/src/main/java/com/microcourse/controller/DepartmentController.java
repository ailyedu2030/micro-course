package com.microcourse.controller;

import com.microcourse.dto.DepartmentCreateRequest;
import com.microcourse.dto.DepartmentUpdateRequest;
import com.microcourse.dto.DepartmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<DepartmentVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<DepartmentVO> result = departmentService.page(page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<DepartmentVO> getById(@PathVariable Long id) {
        DepartmentVO vo = departmentService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
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
}