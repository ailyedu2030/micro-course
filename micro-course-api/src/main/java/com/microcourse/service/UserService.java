package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;

public interface UserService {

    PageResult<UserVO> pageUsers(UserPageQuery query);

    UserVO getUserById(Long id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(Long id, UserUpdateRequest request);

    void updateStatus(Long id, UserStatusRequest request);
}