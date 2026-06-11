package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserRepository extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{username} AND deleted_at IS NULL LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);
}