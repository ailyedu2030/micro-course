/**
 * 教师端 a11y + 回归最小测试集
 * ==============================
 *
 * 职责对齐 (AGENTS.md §浏览器验证默认规则):
 *   - ego-browser: 真实用户式交互走查/截图/手工点选（主审查工具）
 *   - Playwright:  可重复回归、CI 门禁、批量自动化断言（本文件）
 *
 * a11y 门禁规则:
 *   - axe critical/serious 违规（非基线豁免）→ 测试 FAIL（P0/严重）
 *   - axe moderate/minor 违规 → 记录警告，不阻断（但必须在报告中列出）
 *   - 基线豁免: 仅 Element Plus 上游无法控制的项，详见 e2e/a11y-baseline.json
 *   - 截图: fullPage=true 记录完整页面
 *
 * 运行:
 *   npm run test:a11y                              # a11y 专项（全部 @a11y）
 *   npm run test:a11y:smoke                        # 仅 @a11y @smoke
 *   npx playwright test e2e/teacher-audit.spec.js  # 全量回归（含登录流程）
 *   node ../scripts/audit-teacher-pages.mjs        # 教师页面批量审计
 */

// @ts-check
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadBaseline, filterBaselineViolations, shouldBlock, formatViolationSummary } from './a11y-utils.js';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';
const AUTH_USER = process.env.AUTH_USER || 'p0_teacher';
const AUTH_PASS = process.env.AUTH_PASS || 'student123';

const baseline = loadBaseline();

/**
 * 登录教师账号（每个 describe 前置登录一次）
 * 按钮兼容: 主选 .login-btn class，回退文字匹配 "登 录"/"登录"
 */
async function loginAsTeacher(page) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.waitForTimeout(2000);
  await page.fill('input[id="username"]', AUTH_USER);
  await page.fill('input[id="password"]', AUTH_PASS);
<<<<<<< Updated upstream
  // Press Enter to submit (reliable across all Element Plus login form variants)
=======
>>>>>>> Stashed changes
  await page.keyboard.press('Enter');
  await page.waitForTimeout(3000);
}

/**
 * 运行 axe 扫描并执行门禁断言
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} pageName - 路由路径
 * @param {string} projectName - Playwright project 名称
 * @param {import('@playwright/test').TestInfo} testInfo
 */
async function runA11yGate(page, pageName, projectName, testInfo) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
    .analyze();

  // ---- 基线过滤 ----
  const { remaining, removed } = filterBaselineViolations(results.violations, baseline);

  // ---- 记录日志 ----
  const rawCount = results.violations.length;
  const exemptCount = removed.length;
  console.log(`\n[axe] ${projectName} ${pageName}: ${rawCount} 违规 (基线豁免 ${exemptCount}, 实计 ${remaining.length})`);

  if (removed.length > 0) {
    for (const r of removed) {
      console.log(`   ↩ 豁免: ${r.violation.id}[${r.violation.impact}] — ${r.reason}`);
    }
  }

  // ---- 门禁判定 ----
  const gate = shouldBlock(remaining);
  console.log(`   [门禁] critical=${gate.critical} serious=${gate.serious} moderate=${gate.moderate} minor=${gate.minor} | 阻断=${gate.block}`);

  if (gate.block) {
    // 阻断: 列出所有 critical/serious 违规
    const blockingV = remaining.filter(v => v.impact === 'critical' || v.impact === 'serious');
    console.log(`\n   ❌ 阻断违规 (${gate.blocks}):`);
    console.log(formatViolationSummary(blockingV));

    // 非阻断违规也列出
    const nonBlocking = remaining.filter(v => v.impact !== 'critical' && v.impact !== 'serious');
    if (nonBlocking.length > 0) {
      console.log(`\n   ⚠ 非阻断违规 (${nonBlocking.length}):`);
      console.log(formatViolationSummary(nonBlocking));
    }

    // 真实门禁: critical + serious → test FAIL
    expect(gate.blocks).toBe(0);
  } else if (remaining.length > 0) {
    // 全部为 moderate/minor: PASS + 警告
    console.log(`\n   ⚠ 仅有 moderate/minor 违规 (${remaining.length}):`);
    console.log(formatViolationSummary(remaining));
  } else {
    console.log('   ✅ 无不可豁免违规');
  }

  // ---- 全量报告（含已豁免） ----
  testInfo.annotations.push({ type: 'a11y-raw', description: `${rawCount} violations total` });
  testInfo.annotations.push({ type: 'a11y-exempted', description: `${exemptCount} exempted` });
  testInfo.annotations.push({ type: 'a11y-blocking', description: `${gate.blocks} blocking (critical+serious)` });

  console.log(`   [axe] 通过项: ${results.passes.length}, 不完全项: ${results.incomplete.length}`);
}

// ================================================================
// 1. 教师看板 a11y 扫描（smoke）
// ================================================================
test.describe('教师端 - 核心页面 a11y', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTeacher(page);
  });

  test('@a11y @smoke 教师看板 - axe 扫描', async ({ page }, testInfo) => {
    await page.goto(`${BASE_URL}/teacher/dashboard`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);

    // 截图归档（fullPage 记录完整页面，含环境信息文件名）
    await page.screenshot({
      path: testInfo.outputPath(`teacher-dashboard-${testInfo.project.name}-${process.platform}.png`),
      fullPage: true,
    });

    // axe 扫描 + 真实门禁
    await runA11yGate(page, '/teacher/dashboard', testInfo.project.name, testInfo);
  });

  test('@a11y @smoke 教师课程列表 - axe 扫描', async ({ page }, testInfo) => {
    await page.goto(`${BASE_URL}/teacher/courses`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);

    await page.screenshot({
      path: testInfo.outputPath(`teacher-courses-${testInfo.project.name}-${process.platform}.png`),
      fullPage: true,
    });

    await runA11yGate(page, '/teacher/courses', testInfo.project.name, testInfo);
  });

  test('@a11y @smoke 视频管理 - axe 扫描', async ({ page }, testInfo) => {
    await page.goto(`${BASE_URL}/teacher/videos`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);

    await page.screenshot({
      path: testInfo.outputPath(`teacher-videos-${testInfo.project.name}-${process.platform}.png`),
      fullPage: true,
    });

    await runA11yGate(page, '/teacher/videos', testInfo.project.name, testInfo);
  });
});

// ================================================================
// 2. 登录流程验证（不依赖 @a11y 标签）
// ================================================================
test.describe('教师端 - 登录流程', () => {
  test('教师登录成功并跳转到看板', async ({ page }) => {
    await loginAsTeacher(page);
<<<<<<< Updated upstream
    // 导航到看板
    await page.goto(`${BASE_URL}/teacher/dashboard`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    // 验证页面成功渲染（非白屏）
=======
    await page.goto(`${BASE_URL}/teacher/dashboard`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
>>>>>>> Stashed changes
    const content = await page.content();
    expect(content.length).toBeGreaterThan(500);
  });

  test('登录失败显示错误提示', async ({ page }) => {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 });
    await page.waitForTimeout(2000);
    await page.fill('input[id="username"]', 'wrong_user');
    await page.fill('input[id="password"]', 'wrong_pass');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(2000);
    const url = page.url();
    expect(url).toContain('/login');
  });
});
