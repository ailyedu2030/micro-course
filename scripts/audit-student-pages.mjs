#!/usr/bin/env node
/**
 * 学生端全量页面自动化审查脚本 (v1)
 * ====================================
 *
 * 用途：学生页面批量回归 + a11y 扫描 + 性能基线
 * 职责(AGENTS.md §浏览器验证默认规则)：
 *   - ego-browser：本地/开发环境页面联调、交互验证、截图取证（主审查工具）
 *   - Playwright：可重复回归、自动化断言、CI 门禁（本脚本）
 *
 * 运行方式：
 *   node scripts/audit-student-pages.mjs
 *   AUDIT_USERNAME=student AUDIT_PASSWORD=student123 node scripts/audit-student-pages.mjs
 *
 * 环境变量：
 *   AUDIT_BASE_URL   - 目标 URL（默认 http://localhost:8088，拒绝生产 host）
 *   AUDIT_USERNAME   - 学生用户名（默认 student）
 *   AUDIT_PASSWORD   - 密码（默认 student123）
 *   AUDIT_BROWSER    - chromium | firefox | webkit（默认 chromium）
 *   AUDIT_HEADLESS   - true | false（默认 true）
 *   AUDIT_OUTPUT_DIR - 报告输出目录（默认 docs/audit/student-module-audit）
 *   AUDIT_SKIP_A11Y  - true 跳过 axe 扫描（默认 false）
 *
 * 退出码：
 *   0 - 全部通过（含 P2 警告）
 *   1 - 存在 P0 缺陷（critical axe 违规 / 5xx 网络错误 / 页面加载异常）
 *   2 - 存在 P1-C 缺陷（serious axe 违规 / 非预期 4xx / 控制台错误）
 *   3 - 脚本异常
 *   4 - 覆盖率不足（存在 SKIPPED 路由，审计不完整）
 */
/* eslint-env node */

import { createRequire } from 'module';
import { writeFileSync, mkdirSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { release, platform, arch, type } from 'os';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');
const A11Y_UTILS_PATH = resolve(PROJECT_ROOT, 'micro-course-admin/e2e/a11y-utils.js');
const a11yUtils = await import(A11Y_UTILS_PATH);

const ADMIN_NM = resolve(PROJECT_ROOT, 'micro-course-admin/node_modules');
const _require = createRequire(ADMIN_NM);
const { chromium, firefox, webkit } = _require('playwright');
const AxeBuilderClass = _require('@axe-core/playwright');

// ============================================================
// 0. 环境变量 & 常量
// ============================================================
const {
  AUDIT_BASE_URL = 'http://localhost:8088',
  AUDIT_USERNAME = 'student',
  AUDIT_PASSWORD = 'student123',
  AUDIT_BROWSER = 'chromium',
  AUDIT_HEADLESS = 'true',
  AUDIT_OUTPUT_DIR = 'docs/audit/student-module-audit',
  AUDIT_SKIP_A11Y = 'false',
} = process.env;

const HEADLESS = AUDIT_HEADLESS !== 'false';
const SKIP_A11Y = AUDIT_SKIP_A11Y === 'true';
const baseline = a11yUtils.loadBaseline(resolve(PROJECT_ROOT, 'micro-course-admin/e2e/a11y-baseline.json'));

// ============================================================
// 1. 生产环境阻断
// ============================================================
const PRODUCTION_HOSTS = [
  '100.74.122.13',
  'microcourse.ailyedu.cn',
  'microcourse.ailyun.cn',
];
const baseUrl = new URL(AUDIT_BASE_URL);
for (const host of PRODUCTION_HOSTS) {
  if (baseUrl.hostname.includes(host)) {
    console.error(`\n❌ 生产环境阻断: 目标主机包含 ${host}\n`);
    process.exit(3);
  }
}

// ============================================================
// 2. 路由清单 — 全量 STUDENT 可达路由
// ============================================================
const ROUTE_MANIFEST = [
  // ===== 核心 5.1-5.9 模块（静态路由） =====
  { path: '/student/courses',              name: '课程广场',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/my-courses',           name: '我的课程',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/learning',             name: '学习视图',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/learning-stats',       name: '学习中心',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/notifications',        name: '通知中心',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/profile',              name: '个人中心',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/favorites',            name: '收藏',             roles: ['STUDENT'], dynamic: false },
  { path: '/student/discussions',          name: '讨论区',           roles: ['STUDENT'], dynamic: false },
  { path: '/student/exams',                name: '考试列表',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/training',             name: '训练中心',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/settings',             name: '设置',             roles: ['STUDENT'], dynamic: false },
  { path: '/student/achievements',         name: '成就墙',           roles: ['STUDENT'], dynamic: false },
  { path: '/student/orders',               name: '订单列表',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/report',               name: '周报',             roles: ['STUDENT'], dynamic: false },
  { path: '/student/reviews',              name: '我的评价',         roles: ['STUDENT'], dynamic: false },
  { path: '/student/bundles',              name: '课程套件广场',     roles: ['STUDENT'], dynamic: false },
  { path: '/student/my-micro-specialties', name: '我的微专业',       roles: ['STUDENT'], dynamic: false },
  // ===== 动态路由（需解析真实 ID） =====
  { path: '/student/courses/:id',                      name: '课程详情',               roles: ['STUDENT'], dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/student/courses/:id/play/:videoId?',       name: '视频播放器',             roles: ['STUDENT'], dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/student/courses/:courseId/slides/player',  name: '课件播放',               roles: ['STUDENT'], dynamic: true, paramsMap: { courseId: 'courseId' } },
  { path: '/student/chapters/:chapterId/exercises',    name: '随堂练习',               roles: ['STUDENT'], dynamic: true, paramsMap: { chapterId: 'chapterId' } },
  { path: '/student/chapters/:chapterId/offline',      name: '线下课',                 roles: ['STUDENT'], dynamic: true, paramsMap: { chapterId: 'chapterId' } },
  { path: '/student/micro-specialties/:id',            name: '微专业详情',             roles: ['STUDENT'], dynamic: true, paramsMap: { id: 'microSpecialtyId' } },
  { path: '/student/bundles/:id',                      name: '课程套件详情',           roles: ['STUDENT'], dynamic: true, paramsMap: { id: 'bundleId' } },
];

// ============================================================
// 3. 辅助函数
// ============================================================
function extractDynamicParams(path) {
  const params = [];
  const segments = path.split('/');
  for (const seg of segments) {
    if (seg.startsWith(':')) params.push(seg.slice(1));
  }
  return params;
}

function fillDynamicPath(path, values) {
  let result = path;
  for (const [key, val] of Object.entries(values)) {
    result = result.replace(`:${key}`, val);
    result = result.replace(`:${key}?`, val);
  }
  return result;
}

function classifyNetworkErrors(errors) {
  const server5xx = [];
  const client4xx = [];
  const expected4xx = [];
  for (const e of errors) {
    if (e.status >= 500) server5xx.push(e);
    else if (e.status === 401 || e.status === 403) expected4xx.push(e);
    else if (e.status >= 400) client4xx.push(e);
  }
  return { server5xx, client4xx, expected4xx };
}

async function tryResolveDynamicParam(page, paramType) {
  try {
    if (paramType === 'courseId') {
      await page.goto(`${baseUrl}/api/courses?page=0&size=1`, { waitUntil: 'networkidle' });
      const body = await page.evaluate(() => document.body.textContent || '');
      const data = JSON.parse(body);
      const items = data?.data?.items || data?.data || [];
      if (items.length > 0) return items[0].id;
    }
    if (paramType === 'chapterId') {
      await page.goto(`${baseUrl}/api/chapters?size=1`, { waitUntil: 'networkidle' });
      const body = await page.evaluate(() => document.body.textContent);
      const data = JSON.parse(body);
      const items = data?.data?.items || data?.data || [];
      if (items.length > 0) return items[0].id;
    }
    if (paramType === 'microSpecialtyId') {
      await page.goto(`${baseUrl}/api/micro-specialties?page=0&size=1`, { waitUntil: 'networkidle' });
      const body = await page.evaluate(() => document.body.textContent);
      const data = JSON.parse(body);
      const items = data?.data?.items || data?.data || [];
      if (items.length > 0) return items[0].id;
    }
    if (paramType === 'bundleId') {
      await page.goto(`${baseUrl}/api/course-bundles?page=0&size=1`, { waitUntil: 'networkidle' });
      const body = await page.evaluate(() => document.body.textContent);
      const data = JSON.parse(body);
      const items = data?.data?.items || data?.data || [];
      if (items.length > 0) return items[0].id;
    }
    if (paramType === 'videoId') {
      await page.goto(`${baseUrl}/api/videos?page=0&size=1`, { waitUntil: 'networkidle' });
      const body = await page.evaluate(() => document.body.textContent);
      const data = JSON.parse(body);
      const items = data?.data?.items || data?.data || [];
      if (items.length > 0) return items[0].id;
    }
    if (paramType === 'discussionId') {
      await page.goto(`${baseUrl}/api/discussions/posts?page=0&size=1`, { waitUntil: 'networkidle' });
      const body = await page.evaluate(() => document.body.textContent);
      const data = JSON.parse(body);
      const items = data?.data?.items || data?.data || [];
      if (items.length > 0) return items[0].id;
    }
  } catch (e) {
    return null;
  }
  return null;
}

// ============================================================
// 4. 审计运行器
// ============================================================
async function run() {
  console.log(`\n╔══════════════════════════════════════════════════╗`);
  console.log(`║    学生端全量页面自动化审查                      ║`);
  console.log(`╚══════════════════════════════════════════════════╝\n`);

  console.log(`  环境:     ${baseUrl}`);
  console.log(`  用户:     ${AUDIT_USERNAME}`);
  console.log(`  浏览器:   ${AUDIT_BROWSER} (headless: ${HEADLESS})`);
  console.log(`  路由数:   ${ROUTE_MANIFEST.length}`);
  console.log(`  a11y:     ${SKIP_A11Y ? '跳过' : '开启'}`);
  console.log(`  输出:     ${AUDIT_OUTPUT_DIR}\n`);

  const browserType = { chromium, firefox, webkit }[AUDIT_BROWSER] || chromium;
  const contextOptions = {
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    locale: 'zh-CN',
  };

  const browser = await browserType.launch({ headless: HEADLESS });
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();

  // 拦截控制台错误
  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });

  // 收集网络错误
  const networkErrors = [];
  page.on('response', (res) => {
    if (!res.ok()) {
      networkErrors.push({ status: res.status(), url: res.url() });
    }
  });

  // ---- 登录 ----
  console.log(`  [登录] ${AUDIT_USERNAME}...`);
  try {
    await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForSelector('#username', { timeout: 10000 });
    await page.fill('#username', AUDIT_USERNAME);
    await page.fill('#password', AUDIT_PASSWORD);
    const loginBtn = page.locator('.login-btn');
    const loginBtnAlt = page.locator('button:has-text("登 录"), button:has-text("登录")');
    if (await loginBtn.isVisible().catch(() => false)) {
      await loginBtn.click();
    } else {
      await loginBtnAlt.first().click();
    }
    await page.waitForURL('**/student/**', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    console.log(`  [登录] 成功\n`);
  } catch (e) {
    console.error(`  [登录] 失败: ${e.message}`);
    await browser.close();
    process.exit(3);
  }

  // ---- 遍历路由 ----
  const results = [];
  let hasP0 = false;
  let hasP1C = false;
  let skippedCount = 0;

  for (let i = 0; i < ROUTE_MANIFEST.length; i++) {
    const route = ROUTE_MANIFEST[i];
    const result = {
      path: route.path,
      name: route.name,
      status: 'OK',
      violations: [],
      networkErrors: [],
      consoleErrors: [],
      screenshotPath: ''
    };

    process.stdout.write(`  [${String(i + 1).padStart(2, '0')}/${ROUTE_MANIFEST.length}] ${route.name.padEnd(16)} `);

    // P1-I: 清除前一路由的 console/network 错误，避免跨路由累积
    const prevConsoleLen = consoleErrors.length
    result._consoleStart = prevConsoleLen
    networkErrors.length = 0

      result.consoleErrors = routeConsoleErrors;

      // 截图
      const timestamp = Date.now();
      const safePath = resolvedPath.replace(/\//g, '_').replace(/:/g, '');
      const screenshotFile = `${safePath}_${timestamp}.png`;
      const screenshotDir = resolve(PROJECT_ROOT, AUDIT_OUTPUT_DIR, 'screenshots');
      if (!existsSync(screenshotDir)) mkdirSync(screenshotDir, { recursive: true });
      const screenshotPath = resolve(screenshotDir, screenshotFile);
      await page.screenshot({ path: screenshotPath, fullPage: true });
      result.screenshotPath = screenshotFile;

      // axe a11y 扫描
      if (!SKIP_A11Y) {
        try {
          const axeResults = await new AxeBuilderClass({ page })
            .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
            .analyze();
          const { remaining } = a11yUtils.filterBaselineViolations(axeResults.violations, baseline);
          result.violations = remaining;
        } catch (axeErr) {
          console.warn(`\n  ⚠ axe 扫描异常: ${axeErr.message}`);
        }
      }

      // 分类网络错误（排除登录成功后的无关请求）
      const { server5xx, client4xx, expected4xx } = classifyNetworkErrors(
        networkErrors.filter(e => !e.url.includes('/api/auth/'))
      );
      networkErrors.length = 0;

      // 判断状态
      const hasCriticalViolation = result.violations.some(v => v.impact === 'critical');
      const hasSeriousViolation = result.violations.some(v => v.impact === 'serious');
      const has5xx = server5xx.length > 0;
      const hasUnexpected4xx = client4xx.length > 0;

      if (hasCriticalViolation || has5xx) {
        result.status = 'P0';
        hasP0 = true;
      } else if (hasSeriousViolation || hasUnexpected4xx || routeConsoleErrors.length > 0) {
        result.status = 'P1-C';
        hasP1C = true;
      }

      const statusIcon = result.status === 'P0' ? '🔴' : result.status === 'P1-C' ? '🟠' : '✅';
      const extra = [];
      if (result.violations.length > 0) extra.push(`a11y:${result.violations.length}`);
      if (result.consoleErrors.length > 0) extra.push(`console:${result.consoleErrors.length}`);
      console.log(`${statusIcon} ${result.status.padEnd(5)} ${extra.length ? extra.join(' ') : ''}`);

    } catch (navErr) {
      result.status = 'P0';
      result.error = navErr.message;
      hasP0 = true;
      console.log(`🔴 P0 (${navErr.message.slice(0, 60)})`);
    }

    results.push(result);
  }

  await browser.close();

  // ============================================================
  // 5. 生成报告
  // ============================================================
  const totalRoutes = ROUTE_MANIFEST.length;
  const okRoutes = results.filter(r => r.status === 'OK').length;
  const p0Routes = results.filter(r => r.status === 'P0').length;
  const p1cRoutes = results.filter(r => r.status === 'P1-C').length;
  const skippedRoutes = results.filter(r => r.status === 'SKIPPED').length;

  const coverage = totalRoutes > 0
    ? Math.round(((totalRoutes - skippedRoutes) / totalRoutes) * 100)
    : 0;
  const hasDefect = hasP0 || hasP1C;

  const report = {
    timestamp: new Date().toISOString(),
    environment: {
      baseUrl: AUDIT_BASE_URL,
      browser: AUDIT_BROWSER,
      headless: HEADLESS,
      user: AUDIT_USERNAME,
      os: `${type()} ${release()}`,
      arch: arch(),
      node: process.version,
    },
    summary: {
      total: totalRoutes,
      ok: okRoutes,
      p0: p0Routes,
      p1c: p1cRoutes,
      skipped: skippedRoutes,
      coverage: `${coverage}%`,
    },
    routes: results.map(r => ({
      path: r.path,
      name: r.name,
      status: r.status,
      skipReason: r.skipReason || null,
      error: r.error || null,
      violations: r.status === 'OK' || r.status === 'P1-C' || r.status === 'P0'
        ? r.violations.map(v => ({ id: v.id, impact: v.impact, description: v.description }))
        : [],
      consoleErrors: r.consoleErrors.slice(0, 5),
      screenshot: r.screenshotPath || null,
    })),
  };

  // 写 JSON 报告
  const outputDir = resolve(PROJECT_ROOT, AUDIT_OUTPUT_DIR);
  if (!existsSync(outputDir)) mkdirSync(outputDir, { recursive: true });
  const reportPath = resolve(outputDir, `audit-report-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf-8');

  // ============================================================
  // 6. 终屏输出
  // ============================================================
  console.log(`\n╔══════════════════════════════════════════════════╗`);
  console.log(`║  学生端审计报告                                  ║`);
  console.log(`╚══════════════════════════════════════════════════╝\n`);
  console.log(`  环境:     ${AUDIT_BASE_URL}`);
  console.log(`  用户:     ${AUDIT_USERNAME}`);
  console.log(`  时间:     ${new Date().toISOString()}`);
  console.log(`  输出:     ${reportPath}\n`);
  console.log(`  ┌──────────┬──────┬──────┬──────┬──────┬──────────┐`);
  console.log(`  │ 指标     │ 总计  │ 通过  │ P0   │ P1-C │ 跳过     │`);
  console.log(`  ├──────────┼──────┼──────┼──────┼──────┼──────────┤`);
  console.log(`  │ 路由     │ ${String(totalRoutes).padStart(4)} │ ${String(okRoutes).padStart(4)} │ ${String(p0Routes).padStart(4)} │ ${String(p1cRoutes).padStart(4)} │ ${String(skippedRoutes).padStart(4)}    │`);
  console.log(`  └──────────┴──────┴──────┴──────┴──────┴──────────┘`);
  console.log(`  覆盖率:   ${coverage}%\n`);

  // 列出缺陷
  if (hasP0 || hasP1C) {
    console.log(`  缺陷明细:`);
    for (const r of results) {
      if (r.status === 'P0' || r.status === 'P1-C') {
        console.log(`    ${r.status === 'P0' ? '🔴' : '🟠'} [${r.status}] ${r.name} (${r.path})`);
        if (r.violations.length > 0) {
          for (const v of r.violations) {
            console.log(`       a11y: ${v.id}[${v.impact}] — ${v.description.slice(0, 80)}`);
          }
        }
        if (r.consoleErrors.length > 0) {
          console.log(`       console: ${r.consoleErrors[0].slice(0, 100)}`);
        }
      }
    }
    console.log();
  }

  console.log(`  报告已保存至: ${reportPath}`);
  console.log(`  截图已保存至: ${resolve(outputDir, 'screenshots/')}\n`);

  // ============================================================
  // 7. 退出码
  // ============================================================
  if (hasP0) {
    console.log(`🔴 退出码: 1 (存在 P0 缺陷)`);
    process.exit(1);
  }
  if (hasP1C) {
    console.log(`🟠 退出码: 2 (存在 P1-C 缺陷)`);
    process.exit(2);
  }
  if (coverage < 100) {
    console.log(`⚠ 退出码: 4 (覆盖率不足 ${coverage}%)`);
    process.exit(4);
  }
  console.log(`✅ 退出码: 0 (全部通过)`);
  process.exit(0);
}

run().catch((err) => {
  console.error(`\n❌ 脚本异常:`, err);
  process.exit(3);
});
