package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.NarrationSetting;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.NarrationService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.NarrationSettingService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NarrationServiceImpl implements NarrationService {

    private static final Logger log = LoggerFactory.getLogger(NarrationServiceImpl.class);

    private final SlidePageMapper slidePageMapper;
    private final CourseRepository courseRepository;
    private final NarrationSettingService narrationSettingService;
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    @Value("${plugin.interactive.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${plugin.interactive.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${plugin.interactive.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    public NarrationServiceImpl(SlidePageMapper slidePageMapper,
                                CourseRepository courseRepository,
                                NarrationSettingService narrationSettingService,
                                RestTemplate interactiveRestTemplate,
                                TransactionTemplate transactionTemplate) {
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.narrationSettingService = narrationSettingService;
        this.restTemplate = interactiveRestTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public SlidePageVO generate(Long courseId, Integer pageNumber) {
        checkOwner(courseId);

        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                    "需要配置 DEEPSEEK_API_KEY 环境变量");
        }

        SlidePage page = getPage(courseId, pageNumber);
        String currentText = page.getExtractedText();
        if (currentText == null || currentText.isBlank()) {
            currentText = "（本页无可提取文本）";
        }

        String prevContext = "";
        if (pageNumber > 1) {
            try {
                SlidePage prevPage = getPage(courseId, pageNumber - 1);
                String prevText = prevPage.getExtractedText();
                if (prevText != null && !prevText.isBlank()) {
                    prevContext = "上一页内容：\n" + prevText + "\n\n";
                }
            } catch (BusinessException e) {
                // 上一页不存在则忽略
            }
        }

        String systemPrompt = narrationSettingService.buildSystemPrompt(courseId);

        String userPrompt = prevContext
                + "当前幻灯片（第 " + pageNumber + " 页）内容：\n" + currentText
                + "\n\n请结合上一页的内容（如有），为当前页生成连贯的讲述稿，"
                + "注意与上一页之间的过渡衔接，避免突兀。";

        String narrationScript;
        try {
            narrationScript = callDeepSeek(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("[Narration] DeepSeek API failed for courseId={} pageNumber={}", courseId, pageNumber, e);
            throw e;
        }

        final Long pageId = page.getId();
        final String script = narrationScript;
        transactionTemplate.execute(tx -> {
            SlidePage p = slidePageMapper.selectById(pageId);
            if (p == null) return null;
            p.setNarrationScript(script);
            p.setNarrationStatus("AI_GENERATED");
            p.setUpdatedAt(LocalDateTime.now());
            slidePageMapper.updateById(p);
            return null;
        });

        page.setNarrationScript(narrationScript);
        page.setNarrationStatus("AI_GENERATED");

        return toPageVO(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlidePageVO updateScript(Long courseId, Integer pageNumber, String narrationScript) {
        checkOwner(courseId);

        SlidePage page = getPage(courseId, pageNumber);
        deleteOldAudioFile(courseId, pageNumber);
        page.setNarrationScript(narrationScript);
        page.setNarrationStatus("TEACHER_EDITED");
        page.setNarrationAudioUrl(null);
        page.setAudioDuration(null);
        page.setUpdatedAt(LocalDateTime.now());
        slidePageMapper.updateById(page);

        return toPageVO(page);
    }

    @Override
    @Async("slideRenderExecutor")
    public void generateAll(Long courseId) {
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            log.warn("[Narration] DEEPSEEK_API_KEY 未配置，跳过 AI 讲述稿批量生成 courseId={}", courseId);
            return;
        }

        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> allPages = slidePageMapper.selectList(wrapper);
        if (allPages.isEmpty()) {
            log.warn("[Narration] 课程 {} 无幻灯片页面", courseId);
            return;
        }

        StringBuilder pagesContent = new StringBuilder();
        for (SlidePage p : allPages) {
            String text = p.getExtractedText();
            if (text == null || text.isBlank()) {
                text = "（本页无可提取文本）";
            }
            pagesContent.append("===== 第 ").append(p.getPageNumber()).append(" 页 =====\n")
                    .append(text).append("\n\n");
        }

        Integer rawMinutes = narrationSettingService.getByCourseId(courseId).getTotalDurationMinutes();
        int totalMinutes = rawMinutes != null ? rawMinutes : 15;

        String systemPrompt = narrationSettingService.buildSystemPrompt(courseId);
        String userPrompt = "以下是一个课件的全部幻灯片内容（共 " + allPages.size() + " 页）：\n\n"
                + pagesContent.toString()
                + "请为整个课件生成一份连贯的、一气呵成的讲解稿。要求：\n"
                + "① 页与页之间要有自然的过渡语句，整篇讲解要有清晰的故事线和逻辑递进\n"
                + "② 不要重复介绍相同概念，后面提到前面讲过的内容时用「刚才提到的」「前面我们说了」来衔接\n"
                + "③ 总时长约 " + totalMinutes + " 分钟\n"
                + "④ 纯文本，不包含 Markdown 标记\n"
                + "⑤ 请在每页讲述稿前用 【第N页】 标记开头，方便我按页拆分。示例格式：\n\n"
                + "【第1页】\n（第1页的讲述内容）\n\n"
                + "【第2页】\n（第2页的讲述内容，包含与第1页的自然过渡）";

        String fullScript;
        try {
            fullScript = callDeepSeek(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("[Narration] 批量生成连贯讲述稿失败 courseId={}", courseId, e);
            return;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "【第\\s*(\\d+)\\s*页】\\s*([\\s\\S]*?)(?=(【第\\s*\\d+\\s*页】|$))");
        java.util.regex.Matcher matcher = pattern.matcher(fullScript);

        java.util.Map<Integer, String> pageScriptMap = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            int pageNum = Integer.parseInt(matcher.group(1).trim());
            String script = matcher.group(2).trim();
            pageScriptMap.put(pageNum, script);
        }

        if (pageScriptMap.isEmpty() && allPages.size() == 1) {
            pageScriptMap.put(allPages.get(0).getPageNumber(), fullScript);
        }

        final int totalPages = allPages.size();
        final java.util.Map<Integer, String> finalMap = pageScriptMap;
        int savedCount = transactionTemplate.execute(tx -> {
            int n = 0;
            for (SlidePage page : allPages) {
                SlidePage fresh = slidePageMapper.selectById(page.getId());
                if (fresh == null) continue;
                String script = finalMap.get(fresh.getPageNumber());
                if (script != null && !script.isBlank()) {
                    fresh.setNarrationScript(script);
                    fresh.setNarrationStatus("AI_GENERATED");
                    fresh.setUpdatedAt(LocalDateTime.now());
                    slidePageMapper.updateById(fresh);
                    n++;
                }
            }
            return n;
        });

        log.info("[Narration] 批量生成完成 courseId={}, 共 {} 页, 成功 {} 页",
                courseId, totalPages, savedCount);
    }

    private String callDeepSeek(String systemPrompt, String userPrompt) {
        int maxRetries = 3;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(deepseekApiKey);

                Map<String, Object> systemMsg = new LinkedHashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);

                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userPrompt);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", deepseekModel);
                body.put("messages", List.of(systemMsg, userMsg));
                body.put("temperature", 0.7);
                body.put("max_tokens", 4096);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                String url = deepseekBaseUrl + "/v1/chat/completions";

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

                if (response == null) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "DeepSeek 返回空响应");
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "DeepSeek 返回空 choices");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message == null) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "DeepSeek 返回空 message");
                }
                String content = (String) message.get("content");

                if (content == null || content.isBlank()) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "DeepSeek 返回空内容");
                }

                return content.trim();
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("[DeepSeek] 第 {}/{} 次调用超时，准备重试", attempt, maxRetries);
                if (attempt < maxRetries) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < maxRetries) {
                    lastException = e;
                    log.warn("[DeepSeek] 第 {}/{} 次调用限流(429)，准备重试", attempt, maxRetries);
                    try { Thread.sleep(2000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("[DeepSeek] HTTP 错误 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            "AI 讲述稿生成服务暂时不可用", e);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.error("[DeepSeek] 第 {}/{} 次调用异常", attempt, maxRetries, e);
                if (attempt < maxRetries) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("[DeepSeek] 重试 {} 次后仍失败", maxRetries, lastException);
        throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                "AI 讲述稿生成服务暂时不可用，请稍后重试", lastException);
    }

    private SlidePage getPage(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getPageNumber, pageNumber);
        SlidePage page = slidePageMapper.selectOne(wrapper);
        if (page == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        }
        return page;
    }

    private void checkOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private SlidePageVO toPageVO(SlidePage page) {
        SlidePageVO vo = new SlidePageVO();
        vo.setId(page.getId());
        vo.setSlideId(page.getSlideId());
        vo.setCourseId(page.getCourseId());
        vo.setPageNumber(page.getPageNumber());
        vo.setImageUrl(page.getImageUrl());
        vo.setThumbnailUrl(page.getThumbnailUrl());
        vo.setImageWidth(page.getImageWidth());
        vo.setImageHeight(page.getImageHeight());
        vo.setExtractedText(page.getExtractedText());
        vo.setHasAnimation(page.getHasAnimation());
        vo.setHasEmbeddedMedia(page.getHasEmbeddedMedia());
        vo.setNarrationScript(page.getNarrationScript());
        vo.setNarrationAudioUrl(page.getNarrationAudioUrl());
        vo.setAudioDuration(page.getAudioDuration());
        vo.setNarrationStatus(page.getNarrationStatus());
        vo.setNarrationStatusText(SlidePageVO.narrationStatusText(page.getNarrationStatus()));
        vo.setCreatedAt(page.getCreatedAt());
        vo.setUpdatedAt(page.getUpdatedAt());
        return vo;
    }

    private void deleteOldAudioFile(Long courseId, Integer pageNumber) {
        try {
            Path audioPath = Paths.get(storagePath, String.valueOf(courseId), "audio",
                    "page_" + pageNumber + ".mp3");
            boolean deleted = Files.deleteIfExists(audioPath);
            if (deleted) {
                log.info("[Narration] 已清理旧音频 courseId={} page={}", courseId, pageNumber);
            }
        } catch (IOException e) {
            log.warn("[Narration] 清理旧音频失败 courseId={} page={}", courseId, pageNumber, e);
        }
    }
}
