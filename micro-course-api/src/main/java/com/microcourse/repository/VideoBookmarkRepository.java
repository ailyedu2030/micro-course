package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.VideoBookmark;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoBookmarkRepository extends BaseMapper<VideoBookmark> {
}
