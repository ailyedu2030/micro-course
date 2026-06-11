package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.dto.R;
import com.microcourse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC')")
    public R<PageResult<UserVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer status) {
        UserPageQuery query = new UserPageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setKeyword(keyword);
        if (role != null && !role.isEmpty()) {
            query.setRole(com.microcourse.enums.UserRole.valueOf(role));
        }
        query.setDepartmentId(departmentId);
        query.setStatus(status);
        PageResult<UserVO> result = userService.pageUsers(query);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC') or #id == authentication.principal.id")
    public R<UserVO> getById(@PathVariable Long id) {
        UserVO vo = userService.getUserById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        UserVO vo = userService.createUser(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public R<UserVO> update(@PathVariable Long id,
                             @Valid @RequestBody UserUpdateRequest request) {
        UserVO vo = userService.updateUser(id, request);
        return R.ok(vo);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> updateStatus(@PathVariable Long id,
                                 @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request);
        return R.ok();
    }
}