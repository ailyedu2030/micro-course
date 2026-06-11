package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserRepository extends BaseMapper<User> {

    @Select("SELECT id, username, password, real_name, email, phone, gender, role, status, department_id, major_id, class_id, avatar, created_at, updated_at, last_login_at FROM users WHERE username = #{username} AND deleted_at IS NULL LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);
}