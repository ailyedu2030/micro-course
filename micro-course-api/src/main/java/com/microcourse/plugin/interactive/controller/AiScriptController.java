package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.service.AiScriptService;
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
import com.microcourse.service.NarrationSettingService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.util.SecurityUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * v2 课件 AI 讲述稿生成（P3-1 / R-7）。
 * - POST /api/courses/{cid}/ppt/pages/{pageId}/scripts/ai-generate
 * - POST /api/courses/{cid}/html/units/{unitId}/segments/{idx}/ai-generate
 * 替代 ScriptEditor 前端 mock，返回 {scriptText}。
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AiScriptController {

    private final AiScriptService aiScriptService;
    private final PptCoursewareService pptService;
    private final HtmlCoursewareService htmlService;
    private final NarrationSettingService narrationSettingService;
    private final CourseRepository courseRepository;

    public AiScriptController(AiScriptService aiScriptService,
                              PptCoursewareService pptService,
                              HtmlCoursewareService htmlService,
                              NarrationSettingService narrationSettingService,
                              CourseRepository courseRepository) {
        this.aiScriptService = aiScriptService;
        this.pptService = pptService;
        this.htmlService = htmlService;
        this.narrationSettingService = narrationSettingService;
        this.courseRepository = courseRepository;
    }

    @PostMapping("/ppt/pages/{pageId}/scripts/ai-generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Map<String, String>> generatePptScript(@PathVariable Long courseId,
                                                    @PathVariable Long pageId,
                                                    @RequestBody(required = false) Map<String, String> body) {
        checkOwner(courseId);
        SlidePptPageDTO page = pptService.getPage(pageId);
        if (page == null || !courseId.equals(page.getCourseId())) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "PPT page 不存在或不属于该课程");
        }
        String pageText = page.getExtractedText() == null || page.getExtractedText().isBlank()
                ? "（本页无可提取文本）" : page.getExtractedText();
        // 取相邻页作上下文（连贯过渡）
        String prevText = "";
        String nextText = "";
        List<SlidePptPageDTO> pages = pptService.listPagesBySection(page.getSectionId());
        int pageNum = page.getPageNumber() != null ? page.getPageNumber() : 0;
        for (SlidePptPageDTO p : pages) {
            if (p.getPageNumber() != null && p.getPageNumber() == pageNum - 1
                    && p.getExtractedText() != null && !p.getExtractedText().isBlank()) {
                prevText = p.getExtractedText();
            }
            if (p.getPageNumber() != null && p.getPageNumber() == pageNum + 1
                    && p.getExtractedText() != null && !p.getExtractedText().isBlank()) {
                nextText = p.getExtractedText();
            }
        }
        StringBuilder user = new StringBuilder();
        if (!prevText.isBlank()) user.append("上一页内容：\n").append(prevText).append("\n\n");
        user.append("当前幻灯片（第 ").append(pageNum).append(" 页）内容：\n").append(pageText).append("\n\n");
        if (!nextText.isBlank()) user.append("下一页内容（用于衔接预告）：\n").append(nextText).append("\n\n");
        user.append("请为当前页生成连贯的讲述稿，注意与上一页过渡衔接，纯文本，不包含 Markdown 标记。");
        String script = aiScriptService.generate(
                narrationSettingService.buildSystemPrompt(courseId), user.toString());
        return R.ok(Map.of("scriptText", script));
    }

    /**
     * P0-D: 批量 AI 生成 PPT 讲述稿并真实落库（取代前端"生成后不保存"的假完成）。
     * <p>
     * 请求体：{@code {pageIds: [1,2,3], contextType: "page-text"}}。
     * 对每个 pageId：取该页 page_text → LLM 生成 → 保存到 slide_ppt_page_scripts
     * （无 active 脚本创建 v1，有则创建新版本），逐页隔离失败。
     * </p>
     * <p>返回：{@code {results: [{pageId, success, scriptId, error}]}}。</p>
     * 安全：课程级 owner 校验（checkOwner，未越权不触发任何 LLM 调用）；
     * service 内逐页 verifyPageOwner 防跨课程 pageId 枚举。
     */
    @PostMapping("/ppt/pages/scripts/batch-ai-generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<AiScriptService.BatchPptScriptResult>> batchGeneratePptScripts(
            @PathVariable Long courseId,
            @RequestBody(required = false) BatchAiGenerateRequest body) {
        checkOwner(courseId);
        List<Long> pageIds = body != null ? body.pageIds() : null;
        if (pageIds == null || pageIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "pageIds 不能为空");
        }
        return R.ok(aiScriptService.batchGeneratePptScripts(courseId, pageIds));
    }

    @PostMapping("/html/units/{unitId}/segments/{idx}/ai-generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Map<String, String>> generateHtmlSegmentScript(@PathVariable Long courseId,
                                                            @PathVariable Long unitId,
                                                            @PathVariable Integer idx,
                                                            @RequestBody(required = false) Map<String, String> body) {
        checkOwner(courseId);
        // P1-C-4：HTML 分支与 PPT 分支对称 — 校验 unitId 归属 courseId，
        // 防 TEACHER 持自己课程 courseId 枚举/猜测其他课程 unitId（LLM 上下文泄露）。
        SlideHtmlUnitDTO unit = htmlService.getUnit(unitId);
        if (unit == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML 单元不存在");
        }
        if (!courseId.equals(unit.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该 HTML 课件");
        }
        HtmlSegmentScriptDTO seg = htmlService.getActiveSegmentScript(unitId, idx);
        if (seg == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML 分段不存在");
        }
        String segText = seg.getSegmentText() == null || seg.getSegmentText().isBlank()
                ? "（本段无可提取文本）" : seg.getSegmentText();
        String user = "HTML 课件第 " + idx + " 段对应内容：\n" + segText
                + "\n\n请为该段生成讲解讲述稿（约 30-60 秒语速），口语化、自然，纯文本，不包含 Markdown 标记。";
        String script = aiScriptService.generate(
                narrationSettingService.buildSystemPrompt(courseId), user);
        return R.ok(Map.of("scriptText", script));
    }

    private void checkOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    /** P0-D 批量 AI 生成请求体。contextType 保留扩展位（当前恒为 "page-text"）。 */
    public record BatchAiGenerateRequest(List<Long> pageIds, String contextType) {}
}
