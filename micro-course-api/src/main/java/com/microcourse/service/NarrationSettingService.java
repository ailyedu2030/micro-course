package com.microcourse.service;

import com.microcourse.dto.narration.NarrationSettingRequest;
import com.microcourse.dto.narration.NarrationSettingVO;

public interface NarrationSettingService {

    /** 获取课程讲述稿设置，不存在则返回默认值 */
    NarrationSettingVO getByCourseId(Long courseId);

    /** 保存或更新课程讲述稿设置 */
    NarrationSettingVO save(Long courseId, NarrationSettingRequest request);

    /** 构建 AI 生成讲述稿的 system prompt */
    String buildSystemPrompt(Long courseId);
}
