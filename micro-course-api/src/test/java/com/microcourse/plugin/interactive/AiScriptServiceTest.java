package com.microcourse.plugin.interactive;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.service.AiScriptService;
import com.microcourse.plugin.interactive.service.LlmChatClient;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
import com.microcourse.service.NarrationSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-D: 批量 AI 生成（真实落库）单元测试。
 *
 * <p>修复前: 批量 AI 生成仅返回 {scriptText} 不落库, 前端也未保存 →
 * 报"成功 X 页"但刷新后讲述稿消失（假完成）。</p>
 *
 * <p>修复后: {@link AiScriptService#batchGeneratePptScripts} 逐页
 * LLM 生成 + saveScript 落库, 逐页隔离失败, 返回 {pageId, success, scriptId, error}。</p>
 */
class AiScriptServiceTest {

    private LlmChatClient llmChatClient;
    private PptCoursewareService pptService;
    private NarrationSettingService narrationSettingService;
    private AiScriptService service;

    @BeforeEach
    void setUp() {
        llmChatClient = mock(LlmChatClient.class);
        pptService = mock(PptCoursewareService.class);
        narrationSettingService = mock(NarrationSettingService.class);
        service = new AiScriptService(llmChatClient, pptService, narrationSettingService);
        when(narrationSettingService.buildSystemPrompt(anyLong())).thenReturn("[系统提示]");
    }

    private SlidePptPageDTO page(Long id, Long courseId, String text) {
        SlidePptPageDTO p = new SlidePptPageDTO();
        p.setId(id);
        p.setCourseId(courseId);
        p.setExtractedText(text);
        return p;
    }

    @Nested
    @DisplayName("批量生成成功路径（P0-D）")
    class BatchSuccess {

        @Test
        @DisplayName("逐页 LLM 生成 + saveScript 落库 + 返回 scriptId")
        void batchSuccessPersistsScripts() {
            when(pptService.getPage(1L)).thenReturn(page(1L, 10L, "页面一内容"));
            when(pptService.getPage(2L)).thenReturn(page(2L, 10L, "页面二内容"));
            when(llmChatClient.generate(anyString(), anyString()))
                    .thenReturn("讲述稿一").thenReturn("讲述稿二");
            when(pptService.saveScript(eq(1L), anyString(), isNull(), isNull(), any()))
                    .thenReturn(100L);
            when(pptService.saveScript(eq(2L), anyString(), isNull(), isNull(), any()))
                    .thenReturn(200L);

            List<AiScriptService.BatchPptScriptResult> results =
                    service.batchGeneratePptScripts(10L, List.of(1L, 2L));

            assertEquals(2, results.size(), "两页各返回一条结果");
            assertTrue(results.get(0).success());
            assertEquals(100L, results.get(0).scriptId());
            assertNull(results.get(0).error());
            assertTrue(results.get(1).success());
            assertEquals(200L, results.get(1).scriptId());
            // 逐页 IDOR 校验 + 逐页落库
            verify(pptService).verifyPageOwner(10L, 1L);
            verify(pptService).verifyPageOwner(10L, 2L);
            verify(pptService, times(2)).saveScript(anyLong(), anyString(), isNull(), isNull(), any());
            verify(llmChatClient, times(2)).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("生成文本透传 LLM 结果保存（含空 page_text 兜底文案）")
        void batchUsesPageTextAndPersistsLlmResult() {
            when(pptService.getPage(1L)).thenReturn(page(1L, 10L, null)); // 无可提取文本
            when(llmChatClient.generate(anyString(), anyString())).thenReturn("生成的讲述稿");

            List<AiScriptService.BatchPptScriptResult> results =
                    service.batchGeneratePptScripts(10L, List.of(1L));

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            // 生成后的文本必须真正落库（P0-D 核心断言）
            verify(pptService).saveScript(eq(1L), eq("生成的讲述稿"), isNull(), isNull(), any());
        }
    }

    @Nested
    @DisplayName("批量生成失败路径（逐页隔离）")
    class BatchFailure {

        @Test
        @DisplayName("LLM 失败页记为失败, 不落库, 不影响其他页")
        void llmFailureIsolatedPerPage() {
            when(pptService.getPage(1L)).thenReturn(page(1L, 10L, "页面一"));
            when(pptService.getPage(2L)).thenReturn(page(2L, 10L, "页面二"));
            when(llmChatClient.generate(anyString(), anyString()))
                    .thenThrow(new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "LLM 不可用"))
                    .thenReturn("页面二讲述稿");
            when(pptService.saveScript(eq(2L), anyString(), isNull(), isNull(), any()))
                    .thenReturn(200L);

            List<AiScriptService.BatchPptScriptResult> results =
                    service.batchGeneratePptScripts(10L, List.of(1L, 2L));

            assertEquals(2, results.size());
            assertFalse(results.get(0).success(), "页面 1 LLM 失败");
            assertNotNull(results.get(0).error());
            assertTrue(results.get(1).success(), "页面 2 不受页面 1 失败影响");
            verify(pptService, never()).saveScript(eq(1L), anyString(), isNull(), isNull(), any());
            verify(pptService).saveScript(eq(2L), anyString(), isNull(), isNull(), any());
        }

        @Test
        @DisplayName("跨课程 pageId 越权 → 该页失败, 不触发 LLM 与落库")
        void crossCoursePageFails() {
            // verifyPageOwner 抛 NO_PERMISSION（跨课程 pageId 枚举防护）
            doThrow(new BusinessException(ErrorCode.NO_PERMISSION, "该课件页不属于本课程"))
                    .when(pptService).verifyPageOwner(10L, 1L);

            List<AiScriptService.BatchPptScriptResult> results =
                    service.batchGeneratePptScripts(10L, List.of(1L));

            assertEquals(1, results.size());
            assertFalse(results.get(0).success());
            verify(llmChatClient, never()).generate(anyString(), anyString());
            verify(pptService, never()).saveScript(anyLong(), anyString(), isNull(), isNull(), any());
        }

        @Test
        @DisplayName("LLM 返回空白 → 该页失败（防御空串落库）")
        void blankLlmOutputFails() {
            when(pptService.getPage(1L)).thenReturn(page(1L, 10L, "页面一"));
            when(llmChatClient.generate(anyString(), anyString())).thenReturn("   ");

            List<AiScriptService.BatchPptScriptResult> results =
                    service.batchGeneratePptScripts(10L, List.of(1L));

            assertFalse(results.get(0).success());
            verify(pptService, never()).saveScript(anyLong(), anyString(), isNull(), isNull(), any());
        }

        @Test
        @DisplayName("pageId 列表为空 → 返回空结果（不调 LLM）")
        void emptyPageIdsReturnsEmpty() {
            List<AiScriptService.BatchPptScriptResult> results =
                    service.batchGeneratePptScripts(10L, List.of());
            assertTrue(results.isEmpty());
            verify(llmChatClient, never()).generate(anyString(), anyString());
        }
    }
}
