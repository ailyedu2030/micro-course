package com.microcourse.service;

import com.microcourse.entity.NarrationSetting;

public interface NarrationSettingService {

    /** 获取课程讲述稿设置，不存在则返回默认值 */
    NarrationSetting getByCourseId(Long courseId);

    /** 保存或更新课程讲述稿设置 */
    NarrationSetting save(Long courseId, NarrationSetting setting);

    /** 构建 AI 生成讲述稿的 system prompt */
    String buildSystemPrompt(Long courseId);
}
