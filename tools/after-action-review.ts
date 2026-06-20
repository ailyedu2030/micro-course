#!/usr/bin/env tsx
// ===================================================================
// after-action-review.ts — Super-Fix 事后审查（AAR）
//
// 分析 git 历史、审计发现、测试结果，生成结构化 AAR 报告。
// 退出码: 0 = 生成成功
//
// 用法: npx tsx tools/after-action-review.ts [--days=7]
// ===================================================================

import { execSync } from 'child_process';
import { readFileSync, writeFileSync, existsSync } from 'fs';
import { join } from 'path';

const CACHE_DIR = '.audit-cache';
const REPORT_FILE = join(CACHE_DIR, 'after-action-review.md');

interface AARData {
  period: string;
  totalCommits: number;
  commitsByType: Record<string, number>;
  filesChanged: string[];
  findings: {
    total: number;
    open: number;
    fixed: number;
    bySeverity: Record<string, number>;
  };
  risks: string[];
  recommendations: string[];
}

function getGitStats(days: number): AARData {
  const since = new Date(Date.now() - days * 86400000).toISOString();
  
  let commits = '';
  try {
    commits = execSync(
      `git log --since="${since}" --oneline --no-merges`,
      { encoding: 'utf-8', cwd: process.cwd() }
    );
  } catch {
    commits = '';
  }

  const lines = commits.trim().split('\n').filter(l => l.trim());
  const commitsByType: Record<string, number> = {};

  for (const line of lines) {
    const match = line.match(/^[\da-f]+\s+(\w+)\(/);
    const type = match?.[1] || 'other';
    commitsByType[type] = (commitsByType[type] || 0) + 1;
  }

  // Get changed files
  let filesChanged: string[] = [];
  try {
    const files = execSync(
      `git diff --name-only HEAD~${Math.min(lines.length, 50)} HEAD 2>/dev/null || echo ""`,
      { encoding: 'utf-8', cwd: process.cwd() }
    );
    filesChanged = files.trim().split('\n').filter(f => f.trim());
  } catch {
    filesChanged = [];
  }

  // Read findings
  const findingsFile = join(CACHE_DIR, 'findings.json');
  let findingsData = { total: 0, open: 0, fixed: 0, bySeverity: {} as Record<string, number> };

  if (existsSync(findingsFile)) {
    try {
      const findings = JSON.parse(readFileSync(findingsFile, 'utf-8'));
      findingsData = {
        total: findings.length,
        open: findings.filter((f: any) => f.status === 'open').length,
        fixed: findings.filter((f: any) => f.status === 'fixed' || f.status === 'verified').length,
        bySeverity: findings.reduce((acc: Record<string, number>, f: any) => {
          acc[f.severity] = (acc[f.severity] || 0) + 1;
          return acc;
        }, {})
      };
    } catch {
      // ignore parse errors
    }
  }

  return {
    period: `过去 ${days} 天`,
    totalCommits: lines.length,
    commitsByType,
    filesChanged: filesChanged.slice(0, 20),
    findings: findingsData,
    risks: [],
    recommendations: []
  };
}

function generateAAR(data: AARData): string {
  const commitSummary = Object.entries(data.commitsByType)
    .map(([type, count]) => `- ${type}: ${count}`)
    .join('\n') || '- 无提交记录';

  const findingSummary = Object.entries(data.findings.bySeverity)
    .map(([sev, count]) => `- ${sev}: ${count}`)
    .join('\n') || '- 无发现记录';

  // Generate risks
  const risks: string[] = [];
  if (data.findings.open > 0) {
    risks.push(`⚠️ 还有 ${data.findings.open} 个未修复的审计发现`);
  }
  if (data.totalCommits === 0) {
    risks.push('⚠️ 本周期无代码提交，可能影响进度');
  }
  if (data.filesChanged.length > 15) {
    risks.push('⚠️ 修改文件数量较多（>15），需关注变更范围');
  }

  // Generate recommendations
  const recommendations: string[] = [];
  if (data.findings.open > 0) {
    recommendations.push('继续修复剩余的 P0/P1 发现');
  }
  if (data.commitsByType['fix'] && data.commitsByType['fix'] > 3) {
    recommendations.push('修复提交较多，建议加强代码审查和测试覆盖');
  }
  if (data.filesChanged.length > 0) {
    recommendations.push('对修改的文件进行交叉验证');
  }

  data.risks = risks;
  data.recommendations = recommendations;

  return `# Super-Fix After-Action Review (AAR)

> 自动生成 — ${new Date().toISOString()}

## 审查周期
- **周期**: ${data.period}
- **总提交**: ${data.totalCommits}
- **修改文件**: ${data.filesChanged.length}

## 提交统计
${commitSummary}

## 审计发现统计
- **总发现**: ${data.findings.total}
- **已修复**: ${data.findings.fixed}
- **待修复**: ${data.findings.open}

### 按严重度
${findingSummary}

## 风险识别
${data.risks.length > 0 ? data.risks.join('\n') : '- 无重大风险'}

## 改进建议
${data.recommendations.length > 0 ? data.recommendations.join('\n') : '- 继续保持当前质量水平'}

## 关键文件变更
${data.filesChanged.length > 0 ? data.filesChanged.map(f => '- ' + f).join('\n') : '- 无变更记录'}

---
*报告由 after-action-review.ts 自动生成*
`;
}

// Main
const daysArg = process.argv.find(a => a.startsWith('--days='));
const days = daysArg ? parseInt(daysArg.split('=')[1]) || 7 : 7;

console.log('📝 Super-Fix 事后审查 (AAR)');
console.log(`   周期: 过去 ${days} 天`);
console.log('');

const data = getGitStats(days);
const report = generateAAR(data);

// Ensure cache directory exists
if (!existsSync(CACHE_DIR)) {
  execSync(`mkdir -p ${CACHE_DIR}`);
}

writeFileSync(REPORT_FILE, report);
console.log(`✅ AAR 报告已生成: ${REPORT_FILE}`);
console.log('');
console.log('关键指标:');
console.log(`  提交数: ${data.totalCommits}`);
console.log(`  审计发现: ${data.findings.total} (已修复: ${data.findings.fixed}, 待修复: ${data.findings.open})`);
console.log(`  风险项: ${data.risks.length}`);
console.log(`  建议: ${data.recommendations.length}`);
