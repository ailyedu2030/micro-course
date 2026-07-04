package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserVO;

public interface UserQueryService {

    PageResult<UserVO> pageUsers(UserPageQuery query);

    UserVO getUserById(Long id);
}
