package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.NarrationSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NarrationSettingRepository extends BaseMapper<NarrationSetting> {
}
