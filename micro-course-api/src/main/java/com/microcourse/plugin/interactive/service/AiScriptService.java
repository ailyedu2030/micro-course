package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.service.NarrationSettingService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 讲述稿生成服务（P3-1 / R-7）。
 * v2 课件（slide_ppt_pages / slide_html_units）的"AI 生成讲述稿"真实接口，
 * 替代 ScriptEditor 前端 mock；LLM 调用统一走 {@link LlmChatClient}
 * （MiniMax 优先，DeepSeek 兜底，F-2026-08-07-08）。
 * <p>
 * P0-D 修复（批量 AI 生成假完成）：新增 {@link #batchGeneratePptScripts} ——
 * 批量逐页生成讲述稿并<b>真实落库</b>到 slide_ppt_page_scripts
 * （无 active 脚本则创建 v1，有则降级旧版并创建新版本），逐页隔离失败，
 * 取代前端"生成后不保存、刷新即丢失"的假完成。
 * </p>
 */
@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AiScriptService {

    private static final Logger log = LoggerFactory.getLogger(AiScriptService.class);

    private final LlmChatClient llmChatClient;
    private final PptCoursewareService pptService;
    private final NarrationSettingService narrationSettingService;

    public AiScriptService(LlmChatClient llmChatClient,
                           PptCoursewareService pptService,
                           NarrationSettingService narrationSettingService) {
        this.llmChatClient = llmChatClient;
        this.pptService = pptService;
        this.narrationSettingService = narrationSettingService;
    }

    /**
     * 调用 LLM 生成讲述稿（OpenAI 兼容接口，含 3 次重试 + 429/超时退避）。
     */
    public String generate(String systemPrompt, String userPrompt) {
        return llmChatClient.generate(systemPrompt, userPrompt);
    }

    /**
     * P0-D: 批量 AI 生成 PPT 讲述稿并真实落库。
     * <p>
     * 对每个 pageId：
     * <ol>
     *   <li>逐页 IDOR 校验（verifyPageOwner：page 属于该 course + 当前用户是 owner）</li>
     *   <li>取该 page 的 extractedText 组装 LLM 用户提示</li>
     *   <li>LLM 生成讲述稿</li>
     *   <li>saveScript 落库（无 active → v1；有 → 降级旧版 + 新版本）</li>
     *   <li>记录成功/失败，单页异常不阻断批次</li>
     * </ol>
     * </p>
     *
     * @param courseId 课程 id（写操作前置校验已在 controller 完成课程级 owner 校验）
     * @param pageIds  目标 PPT 页 id 列表（非空由 controller 保证）
     * @return 逐页结果列表，顺序与入参一致
     */
    public List<BatchPptScriptResult> batchGeneratePptScripts(Long courseId, List<Long> pageIds) {
        List<BatchPptScriptResult> results = new ArrayList<>(pageIds.size());
        if (pageIds == null || pageIds.isEmpty()) {
            return results;
        }
        String systemPrompt = narrationSettingService.buildSystemPrompt(courseId);
        for (Long pageId : pageIds) {
            try {
                // IDOR: 每页校验归属 + owner（跨课程 pageId 枚举/篡改 → 该页记为失败）
                pptService.verifyPageOwner(courseId, pageId);
                SlidePptPageDTO page = pptService.getPage(pageId);
                String pageText = page.getExtractedText() == null || page.getExtractedText().isBlank()
                        ? "（本页无可提取文本）" : page.getExtractedText();
                String user = "当前幻灯片内容：\n" + pageText
                        + "\n\n请为当前页生成连贯的讲述稿，口语化、自然，约 30-60 秒语速，"
                        + "纯文本，不包含 Markdown 标记。";
                String script = llmChatClient.generate(systemPrompt, user);
                if (script == null || script.isBlank()) {
                    results.add(BatchPptScriptResult.fail(pageId, "AI 生成返回为空，请重试"));
                    continue;
                }
                // 落库：无 active 脚本 → 创建 v1；有 → 降级旧版并创建新版本（saveScript 契约）
                Long scriptId = pptService.saveScript(pageId, script, null, null,
                        SecurityUtil.getCurrentUserIdOpt());
                log.info("[PPT-AI-Batch] 生成并保存成功: pageId={}, scriptId={}", pageId, scriptId);
                results.add(BatchPptScriptResult.ok(pageId, scriptId));
            } catch (Exception e) {
                log.warn("[PPT-AI-Batch] 页面生成失败: pageId={}, err={}", pageId, e.getMessage());
                results.add(BatchPptScriptResult.fail(pageId, e.getMessage()));
            }
        }
        return results;
    }

    /** P0-D 批量结果条目：pageId + success + scriptId/error。 */
    public record BatchPptScriptResult(Long pageId, boolean success, Long scriptId, String error) {
        public static BatchPptScriptResult ok(Long pageId, Long scriptId) {
            return new BatchPptScriptResult(pageId, true, scriptId, null);
        }

        public static BatchPptScriptResult fail(Long pageId, String error) {
            return new BatchPptScriptResult(pageId, false, null, error);
        }
    }
}
