/**
 * a11y 通用工具函数
 * ====================
 *
 * 共享 teacher-audit.spec.js 与 audit-teacher-pages.mjs 的基线加载、
 * 违规分类和断言逻辑，保证两边行为一致。
 *
 * 职责：
 *   - 加载并解析 a11y-baseline.json
 *   - 按豁免基线过滤违规
 *   - 按 axe impact 严重程度分类
 *   - 判定测试是否应失败
 */

// @ts-check

import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

/**
 * @typedef {{ exemptions: Array<{ruleId: string, selectors?: string[], reason: string, expiresAt: string}>, knownViolations: Array<{ruleId: string, impact: string, reason: string}>, notes?: string }} Baseline
 */

/** @type {Baseline} */
let _baseline = null;
/** @type {string|null} */
let _baselinePath = null;
/** @type {string|null} */
let _baselineLoadedFrom = null;

/**
 * 返回 a11y-baseline.json 的绝对路径
 */
export function baselinePath() {
  if (!_baselinePath) {
    _baselinePath = resolve(__dirname, 'a11y-baseline.json');
  }
  return _baselinePath;
}

/**
 * 加载 a11y 豁免基线
 * @param {string} [customPath] 可选的基线文件路径（用于从 scripts/ 目录加载）
 * @returns {{ exemptions: Array<{ruleId: string, selectors?: string[], reason: string, expiresAt: string}>, knownViolations: Array<{ruleId: string, impact: string, reason: string}>, notes?: string }}
 */
export function loadBaseline(customPath) {
  const path = customPath || baselinePath();

  // 按路径缓存：只有相同路径才复用缓存
  if (_baseline && _baselineLoadedFrom === path) return _baseline;

  if (!existsSync(path)) {
    console.warn(`[a11y-utils] 基线文件不存在: ${path}，使用空基线`);
    _baseline = { exemptions: [], knownViolations: [] };
    _baselineLoadedFrom = path;
    return _baseline;
  }

  try {
    const raw = readFileSync(path, 'utf-8');
    _baseline = JSON.parse(raw);
    // 确保结构完整
    if (!_baseline.exemptions) _baseline.exemptions = [];
    if (!_baseline.knownViolations) _baseline.knownViolations = [];
    _baselineLoadedFrom = path;
    return _baseline;
  } catch (err) {
    console.warn(`[a11y-utils] 基线文件解析失败: ${err.message}，使用空基线`);
    _baseline = { exemptions: [], knownViolations: [] };
    _baselineLoadedFrom = path;
    return _baseline;
  }
}

/**
 * 清除缓存（用于测试）
 */
export function resetBaseline() {
  _baseline = null;
  _baselineLoadedFrom = null;
}

/**
 * 检查某项违规是否被基线豁免
 * 匹配规则: ruleId 必须相同；如果豁免有 selectors 限制，违规节点中至少一个 target 匹配
 * @param {{ id: string, nodes: Array<{target?: string[]}>, impact?: string }} violation
 * @param {{ exemptions: Array<{ruleId: string, selectors?: string[]}> }} baseline
 * @returns {{ exempted: boolean, reason?: string }}
 */
export function isExempted(violation, baseline) {
  if (!baseline || !baseline.exemptions) return { exempted: false };

  for (const ex of baseline.exemptions) {
    if (ex.ruleId !== violation.id) continue;

    // 无 selectors 限制 -> 整条规则豁免
    if (!ex.selectors || ex.selectors.length === 0) {
      return { exempted: true, reason: ex.reason };
    }

    // 检查违规节点的 target 或 html 是否命中任一个豁免选择器
    if (violation.nodes && violation.nodes.length > 0) {
      for (const node of violation.nodes) {
        const targets = node.target || [];
        for (const t of targets) {
          if (ex.selectors.some(sel => t.includes(sel) || t === sel)) {
            return { exempted: true, reason: ex.reason };
          }
        }
        // 部分 EP 组件使用动态 id 作为 target（如 #el-id-xxx），
        // 补充检查 node.html 是否包含豁免选择器中的子串
        if (node.html) {
          if (ex.selectors.some(sel => node.html.includes(sel))) {
            return { exempted: true, reason: ex.reason };
          }
        }
      }
    }
  }

  return { exempted: false };
}

/**
 * 从违规列表中移除被基线豁免的项
 * @param {Array<{id: string, nodes: Array<{target?: string[]}>, impact?: string}>} violations
 * @param {{ exemptions: Array<{ruleId: string, selectors?: string[], reason: string}> }} baseline
 * @returns {{ remaining: Array, removed: Array<{violation: *, reason: string}> }}
 */
export function filterBaselineViolations(violations, baseline) {
  const remaining = [];
  const removed = [];

  for (const v of violations) {
    const { exempted, reason } = isExempted(v, baseline);
    if (exempted) {
      removed.push({ violation: v, reason: reason || '基线豁免' });
    } else {
      remaining.push(v);
    }
  }

  return { remaining, removed };
}

/**
 * 按 axe impact 严重程度分类
 * @param {Array<{id: string, impact?: string}>} violations
 * @returns {{ critical: Array, serious: Array, moderate: Array, minor: Array }}
 */
export function classifyViolations(violations) {
  /** @type {{ critical: Array, serious: Array, moderate: Array, minor: Array }} */
  const classified = { critical: [], serious: [], moderate: [], minor: [] };

  for (const v of violations) {
    const impact = v.impact || 'minor';
    if (classified[impact]) {
      classified[impact].push(v);
    } else {
      classified.minor.push(v);
    }
  }

  return classified;
}

/**
 * 判断给定违规集合是否应阻断测试（门禁逻辑）
 *
 * 阻断规则:
 *   - critical 违规 → 阻断（P0 等价）
 *   - serious 违规  → 阻断（严重 WCAG 违规）
 *   - moderate/minor → 不阻断（记录警告）
 *
 * @param {Array} violations 已过滤基线后的违规列表
 * @returns {{ block: boolean, critical: number, serious: number, moderate: number, minor: number, blocks: number }}
 */
export function shouldBlock(violations) {
  const bySeverity = classifyViolations(violations);
  const critical = bySeverity.critical.length;
  const serious = bySeverity.serious.length;
  const blocks = critical + serious;

  return {
    block: blocks > 0,
    critical,
    serious,
    moderate: bySeverity.moderate.length,
    minor: bySeverity.minor.length,
    blocks,
  };
}

/**
 * 格式化违规摘要（用于控制台输出）
 * @param {Array} violations
 * @param {number} [maxNodes=3]
 * @returns {string}
 */
export function formatViolationSummary(violations, maxNodes = 3) {
  if (violations.length === 0) return '  无违规';

  const lines = [];
  for (const v of violations) {
    const nodes = v.nodes || [];
    const firstTargets = nodes.slice(0, maxNodes).map(n => n.target?.[0] || '?');
    lines.push(`  ⛔ ${v.id} [${v.impact}] ${v.description} (${nodes.length} 节点)`);
    firstTargets.forEach((t, i) => {
      lines.push(`     #${i + 1}: ${t}`);
    });
    if (nodes.length > maxNodes) {
      lines.push(`     ... 还有 ${nodes.length - maxNodes} 个节点`);
    }
  }
  return lines.join('\n');
}

export default {
  loadBaseline,
  resetBaseline,
  isExempted,
  filterBaselineViolations,
  classifyViolations,
  shouldBlock,
  formatViolationSummary,
  baselinePath,
};
