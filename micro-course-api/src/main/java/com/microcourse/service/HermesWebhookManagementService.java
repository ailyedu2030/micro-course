package com.microcourse.service;

import com.microcourse.dto.hermes.HermesChapterRequest;
import com.microcourse.dto.hermes.HermesChapterVO;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesSectionRequest;
import com.microcourse.dto.hermes.HermesSectionVO;

import java.util.List;

/**
 * Hermes Webhook 管理 Service。
 * 处理章节/课时的独立 CRUD、课程级联删除、课程列表查询及 API Key 管理。
 */
public interface HermesWebhookManagementService {

    /**
     * 列出某课程的所有课时。
     */
    List<HermesSectionVO> listSections(String hermesCourseId, String apiKey);

    /**
     * 创建课时。
     */
    HermesSectionVO createSection(String hermesCourseId, String apiKey, HermesSectionRequest body);

    /**
     * 更新课时。
     */
    HermesSectionVO updateSection(String hermesCourseId, String apiKey, Long sectionId, HermesSectionRequest body);

    /**
     * 更新章节。
     */
    HermesChapterVO updateChapter(String hermesCourseId, String apiKey, Long chapterId, HermesChapterRequest body);

    /**
     * 级联删除课程（含 Hermes 映射清理）。
     */
    void deleteCourse(String hermesCourseId, String apiKey);

    /**
     * 按内部 ID 删除课程（不依赖 Hermes 映射）。
     */
    void deleteCourseById(Long courseId, String apiKey);

    /**
     * 列出平台所有课程（含非 Hermes 创建的）。
     */
    List<HermesCourseListVO> listAllCourses(String apiKey);

    /**
     * 轮换当前调用方的 API Key。
     */
    String refreshApiKey(String apiKey);

    /**
     * 验证 API Key 并解析出调用方用户。
     */
    com.microcourse.entity.User authenticate(String apiKey);

    /**
     * 根据 hermesCourseId 解析课程映射。
     */
    com.microcourse.entity.HermesCourseMapping resolveMapping(String hermesCourseId);

    /**
     * 验证调用者拥有该课程（ADMIN 或 course.owner == caller）。
     */
    void verifyCourseOwnership(com.microcourse.entity.User caller, com.microcourse.entity.HermesCourseMapping mapping);
}
