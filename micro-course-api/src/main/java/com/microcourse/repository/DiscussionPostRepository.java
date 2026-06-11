package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.DiscussionPost;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DiscussionPostRepository extends BaseMapper<DiscussionPost> {
}