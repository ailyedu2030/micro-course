package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;

/**
 * 课件读侧统一服务 (CQRS Query 模式).
 *
 * 单一职责: 把 PptCoursewareService + HtmlCoursewareService 的查询结果
 * 合并成一个 CoursewareTreeDTO, 前端一次拿全, 减少 HTTP 调用.
 *
 * 性能预算 (spec 6.3): p99 < 200ms (15 页 + 15 音频 + flow).
 * 缓存: Redis mc:courseware:{sectionId}:meta (TTL 10min, 失效于 CRUD).
 */
public interface CoursewareQueryService {

    /**
     * 取一个 section 的完整课件树.
     *
     * 【审计修复 BUG #4】 必须传入 courseId 用于校验 sectionId 归属于该 course,
     * 防止同 sectionId 跨 course 误读数据 (虽然概率极低, 但 REST 安全原则要求
     * 路径变量与资源 ID 双重验证).
     *
     * @param courseId  课程 ID (来自 path)
     * @param sectionId 课时 ID (来自 path)
     * @return CoursewareTreeDTO (type = PPT / HTML / EMPTY)
     */
    CoursewareTreeDTO getCoursewareTree(Long courseId, Long sectionId);

    /**
     * 按 audio_token 流式 GET (7-19 P1-C 兼容).
     * 不依赖 pageNumber, 不依赖 courseId.
     * @param token audio token (32 字符 UUID)
     * @return AudioStreamInfo 含 courseId 与 pptPageId/segmentIndex
     */
    AudioStreamInfo resolveAudioToken(String token);
}