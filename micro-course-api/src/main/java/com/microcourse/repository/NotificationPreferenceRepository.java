package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.NotificationPreference;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationPreferenceRepository extends BaseMapper<NotificationPreference> {
}