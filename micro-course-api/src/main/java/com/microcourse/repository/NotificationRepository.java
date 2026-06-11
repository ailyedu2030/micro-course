package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationRepository extends BaseMapper<Notification> {
}