package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.DiscussionComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DiscussionCommentRepository extends BaseMapper<DiscussionComment> {
}