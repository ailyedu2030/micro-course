package com.microcourse.controller;

import com.microcourse.dto.ClassCreateRequest;
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

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<ClassVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 200) int size) {
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
    @PreAuthorize("hasRole('ADMIN')")
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
}
