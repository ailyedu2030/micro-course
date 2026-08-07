/**
 * Admin 审计 API（F1 任务 4 · D-1 幽灵章节闭环）
 * /api/admin/audit/* 接口封装
 *
 * 响应均为 R 包装：{ code, message, data }，data 为审计报告 JSON 字符串
 * （后端 audit_ghost_chapters() / runGhostChapterFix() 输出，前端 JSON.parse 使用）。
 */
import request from '../utils/request'

/**
 * 幽灵章节只读审计
 * GET /api/admin/audit/ghost-chapters
 * 仅 ADMIN；返回 JSON 文本：{ audited_at, audit_version, total_ghost_rows,
 * by_course:[{course_id,source_type,cnt}], sample_rows:[≤200 行明细], note }
 */
export function getGhostChapterAudit() {
  return request({ method: 'GET', url: '/admin/audit/ghost-chapters' })
}

/**
 * 幽灵章节 V332 幂等自动修复（可重跑）
 * POST /api/admin/audit/run-v332-fix
 * 仅 ADMIN；返回修复完成后审计报告 JSON 文本（对比修复前 total_ghost_rows 看进度）
 */
export function runV332Fix() {
  return request({ method: 'POST', url: '/admin/audit/run-v332-fix' })
}
