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

    @Value("${plugin.interactive.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${plugin.interactive.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${plugin.interactive.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    public NarrationServiceImpl(SlidePageMapper slidePageMapper,
                                CourseRepository courseRepository,
                                NarrationSettingService narrationSettingService) {
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.narrationSettingService = narrationSettingService;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 加载上一页内容作为上下文，确保连贯性
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
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek API failed for courseId={} pageNumber={}", courseId, pageNumber, e);
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                    "DeepSeek API 调用失败: " + e.getMessage(), e);
        }

        page.setNarrationScript(narrationScript);
        page.setNarrationStatus("AI_GENERATED");
        page.setUpdatedAt(LocalDateTime.now());
        slidePageMapper.updateById(page);

        return toPageVO(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlidePageVO updateScript(Long courseId, Integer pageNumber, String narrationScript) {
        checkOwner(courseId);

        SlidePage page = getPage(courseId, pageNumber);
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

        // 获取课件的全部页面（按页码排序）
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> allPages = slidePageMapper.selectList(wrapper);
        if (allPages.isEmpty()) {
            log.warn("[Narration] 课程 {} 无幻灯片页面", courseId);
            return;
        }

        // 构建全量 prompt：将所有页面内容一次性发送给 DeepSeek
        StringBuilder pagesContent = new StringBuilder();
        for (SlidePage p : allPages) {
            String text = p.getExtractedText();
            if (text == null || text.isBlank()) {
                text = "（本页无可提取文本）";
            }
            pagesContent.append("===== 第 ").append(p.getPageNumber()).append(" 页 =====\n")
                    .append(text).append("\n\n");
        }

        int totalMinutes = narrationSettingService.getByCourseId(courseId).getTotalDurationMinutes() != null
                ? narrationSettingService.getByCourseId(courseId).getTotalDurationMinutes() : 15;

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

        // 解析返回结果，按 【第N页】 标记拆分为各页讲述稿
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "【第\\s*(\\d+)\\s*页】\\s*([\\s\\S]*?)(?=(【第\\s*\\d+\\s*页】|$))");
        java.util.regex.Matcher matcher = pattern.matcher(fullScript);

        java.util.Map<Integer, String> pageScriptMap = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            int pageNum = Integer.parseInt(matcher.group(1).trim());
            String script = matcher.group(2).trim();
            pageScriptMap.put(pageNum, script);
        }

        // 将解析出的讲述稿写入对应页面
        if (pageScriptMap.isEmpty() && allPages.size() == 1) {
            // 单页时可能没有标记，整篇作为当前页的讲述稿
            pageScriptMap.put(allPages.get(0).getPageNumber(), fullScript);
        }

        int savedCount = 0;
        for (SlidePage page : allPages) {
            String script = pageScriptMap.get(page.getPageNumber());
            if (script != null && !script.isBlank()) {
                page.setNarrationScript(script);
                page.setNarrationStatus("AI_GENERATED");
                page.setUpdatedAt(LocalDateTime.now());
                slidePageMapper.updateById(page);
                savedCount++;
                try { Thread.sleep(100); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("[Narration] 批量生成完成 courseId={}, 共 {} 页, 成功 {} 页",
                courseId, allPages.size(), savedCount);
    }

    private String callDeepSeek(String systemPrompt, String userPrompt) {
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
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED);
        }
        String content = (String) message.get("content");

        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED);
        }

        return content.trim();
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
        return new SlidePageVO() {{
            setId(page.getId());
            setSlideId(page.getSlideId());
            setCourseId(page.getCourseId());
            setPageNumber(page.getPageNumber());
            setImageUrl(page.getImageUrl());
            setThumbnailUrl(page.getThumbnailUrl());
            setImageWidth(page.getImageWidth());
            setImageHeight(page.getImageHeight());
            setExtractedText(page.getExtractedText());
            setHasAnimation(page.getHasAnimation());
            setHasEmbeddedMedia(page.getHasEmbeddedMedia());
            setNarrationScript(page.getNarrationScript());
            setNarrationAudioUrl(page.getNarrationAudioUrl());
            setAudioDuration(page.getAudioDuration());
            setNarrationStatus(page.getNarrationStatus());
            setNarrationStatusText(SlidePageVO.narrationStatusText(page.getNarrationStatus()));
            setCreatedAt(page.getCreatedAt());
            setUpdatedAt(page.getUpdatedAt());
        }};
    }
}
