package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseBundleItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseBundleItemRepository extends BaseMapper<CourseBundleItem> {
}
