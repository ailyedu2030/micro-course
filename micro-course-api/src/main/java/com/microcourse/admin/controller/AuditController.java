package com.microcourse.admin.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 审计后台统一入口（F1 任务 4 · D-1 闭环）。
 *
 * <p>幽灵章节（V310 COALESCE(chapter_id,1) 硬编码兜底产生的错误归属）审计与修复：
 * <ul>
 *   <li>{@code GET  /api/admin/audit/ghost-chapters} — 只读审计（V328 audit_ghost_chapters()）</li>
 *   <li>{@code POST /api/admin/audit/run-v332-fix} — 幂等自动修复（V332 逻辑，可重跑）</li>
 * </ul>
 *
 * <p>权限：两个端点均为 ADMIN-only（项目权限模型 UserRole 无 DBA 角色——仅
 * STUDENT/TEACHER/ADMIN/ACADEMIC，故"仅 DBA 角色"约束等价回退为 ADMIN，即生产
 * DBA 人工执行约束的最小集）；方法级 {@code @PreAuthorize} + 声明式
 * {@link AuditedLog} 审计留痕双重防护。前端路由 meta.requiresAdmin 同步兜底。</p>
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final CoursewareQueryService queryService;

    public AuditController(CoursewareQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 幽灵章节只读审计：GET /api/admin/audit/ghost-chapters
     *
     * @return JSON 文本报告（audit_ghost_chapters() 输出：
     *         total_ghost_rows / by_course / sample_rows / note）
     */
    @GetMapping("/ghost-chapters")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("幽灵章节审计")
    public R<String> auditGhostChapters() {
        log.info("[AuditController] 幽灵章节审计请求");
        return R.ok(queryService.auditGhostChapters());
    }

    /**
     * 幽灵章节幂等自动修复（V332 逻辑）：POST /api/admin/audit/run-v332-fix
     *
     * <p>对 chapter_id=1 且可通过 section_id 反查到真实 chapter 的记录 UPDATE 修正；
     * section 缺失 / 跨课程引用等无法自动判定的记录保持原样并写 operation_logs
     * 待人工 review。与 V332 migration 幂等，允许任意时刻重跑。</p>
     *
     * @return JSON 文本报告（修复完成后 audit_ghost_chapters() 输出，
     *         前端可对比修复前 total_ghost_rows 验证修复进度）
     */
    @PostMapping("/run-v332-fix")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("幽灵章节V332自动修复")
    public R<String> runV332Fix() {
        log.info("[AuditController] 幽灵章节 V332 幂等修复请求");
        return R.ok(queryService.runGhostChapterFix());
    }
}
