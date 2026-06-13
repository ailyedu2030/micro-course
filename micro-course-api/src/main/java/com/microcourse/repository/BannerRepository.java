package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Banner;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BannerRepository extends BaseMapper<Banner> {
}