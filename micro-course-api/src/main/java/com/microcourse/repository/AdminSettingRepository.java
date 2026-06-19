package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.AdminSetting;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 系统配置 Mapper
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Mapper
public interface AdminSettingRepository extends BaseMapper<AdminSetting> {

    /**
     * P1-3: PostgreSQL 原子 upsert，消除 check-then-insert 竞态
     * INSERT ... ON CONFLICT (setting_key) DO UPDATE
     */
    @Insert("INSERT INTO admin_settings (setting_key, setting_value, updated_at) " +
            "VALUES (#{settingKey}, #{settingValue}, #{updatedAt}) " +
            "ON CONFLICT (setting_key) DO UPDATE " +
            "SET setting_value = EXCLUDED.setting_value, updated_at = EXCLUDED.updated_at")
    int upsertByKey(@Param("settingKey") String settingKey,
                    @Param("settingValue") String settingValue,
                    @Param("updatedAt") LocalDateTime updatedAt);
}