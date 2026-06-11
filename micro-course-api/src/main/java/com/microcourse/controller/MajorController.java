package com.microcourse.controller;

import com.microcourse.dto.MajorCreateRequest;
import com.microcourse.dto.MajorUpdateRequest;
import com.microcourse.dto.MajorVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.MajorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/majors")
public class MajorController {

    private final MajorService majorService;

    @Autowired
    public MajorController(MajorService majorService) {
        this.majorService = majorService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<MajorVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<MajorVO> result = majorService.page(page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<MajorVO> getById(@PathVariable Long id) {
        MajorVO vo = majorService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<MajorVO> create(@Valid @RequestBody MajorCreateRequest request) {
        MajorVO vo = majorService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<MajorVO> update(@PathVariable Long id,
                             @Valid @RequestBody MajorUpdateRequest request) {
        MajorVO vo = majorService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        majorService.delete(id);
        return R.ok();
    }
}