package com.microcourse.service;

import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.SettingUpdateRequest;

import java.util.List;

/**
 * 系统配置服务接口
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
public interface AdminSettingService {

    /**
     * 获取所有系统配置
     *
     * @return 配置列表
     */
    List<AdminSettingVO> getAll();

    /**
     * 根据 key 获取单个配置值
     *
     * @param key 配置键
     * @return 配置值（未找到返回 null）
     */
    String getByKey(String key);

    /**
     * 更新单个配置
     *
     * @param key   配置键
     * @param value 配置值
     */
    void update(String key, String value);

    /**
     * 批量更新配置
     *
     * @param settings 配置更新请求列表
     */
    void updateBatch(List<SettingUpdateRequest> settings);

    /**
     * Upsert 单个配置（key 不存在则插入，存在则更新）
     *
     * @param key   配置键
     * @param value 配置值
     */
    void upsert(String key, String value);

    /**
     * Upsert 单个配置（含 valueType）
     *
     * @param key       配置键
     * @param value     配置值
     * @param valueType 值类型（BOOLEAN / NUMBER / STRING / JSON），null 默认 STRING
     */
    void upsert(String key, String value, String valueType);
}