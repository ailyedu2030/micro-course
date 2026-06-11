package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.AdminSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Mapper
public interface AdminSettingRepository extends BaseMapper<AdminSetting> {
}