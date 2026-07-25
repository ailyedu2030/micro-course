#!/usr/bin/env node
/**
 * 教师端全量页面自动化审查脚本 (v4)
 * ====================================
 *
 * 用途：教师页面批量回归 + a11y 扫描 + 性能基线
 * 职责(AGENTS.md §浏览器验证默认规则)：
 *   - ego-browser：本地/开发环境页面联调、交互验证、截图取证（主审查工具）
 *   - Playwright：可重复回归、自动化断言、CI 门禁（本脚本）
 *
 * v3 变更:
 *   - axe 违规按 severity 分级：critical→P0, serious→P1-C, moderate/minor→P2
 *   - 动态路由无法解析时记 SKIPPED 并从 OK 剔除
 *   - coverage < 100% → 退出码 4（覆盖率不足）
 *   - 网络 4xx 与 5xx 分别处理：预期 403/404 降级警告，5xx→P0
 *   - 截图 fullPage=true
 *   - WebKit 标注 "Safari兼容代理" 非真实 Safari
 *   - 环境信息记录在报告中
 *
 * v4 变更:
 *   - 登录按钮使用 .login-btn 主选择器 + 文本回退
 *   - ROUTE_MANIFEST 补充至全量 TEACHER 可达路由（含 student/ 共享路由）
 *   - requiresLead 路由不再无条件跳过：先通过 readonly API 尝试解析当前用户的
 *     LEAD 微专业 ID，成功则实际访问；失败则标记 fixture 缺失并 exit 4
 *   - 动态参数解析扩展至 courseId / chapterId / discussionId / microSpecialtyId / storageApplicationId
 *   - 退出码优先级修正：P0(exit 1) > P1-C(exit 2) > 覆盖率不足(exit 4, 仅无缺陷时) > 全通过(exit 0)
 *   - 同时存在 P0/P1-C 和 SKIPPED 时：exit code 先按缺陷等级，报告中同时记录 coverage
 *   - a11y-utils.test.mjs 纳入 npm test:a11y:unit
 *
 * 运行方式：
 *   node scripts/audit-teacher-pages.mjs
 *   AUDIT_USERNAME=teacher1 AUDIT_PASSWORD=password123 node scripts/audit-teacher-pages.mjs
 *
 * 环境变量：
 *   AUDIT_BASE_URL   - 目标 URL（默认 http://localhost:8088，拒绝生产 host）
 *   AUDIT_USERNAME   - 教师用户名（默认 teacher1）
 *   AUDIT_PASSWORD   - 密码（默认 password123）
 *   AUDIT_BROWSER    - chromium | firefox | webkit（默认 chromium）
 *   AUDIT_HEADLESS   - true | false（默认 true）
 *   AUDIT_OUTPUT_DIR - 报告输出目录（默认 docs/audit/teacher-module-audit）
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
import { resolve, dirname, basename } from 'path';
import { fileURLToPath } from 'url';
import { release, platform, arch, type } from 'os';

// ---- 导入项目内 a11y 工具 ----
const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');
const A11Y_UTILS_PATH = resolve(PROJECT_ROOT, 'micro-course-admin/e2e/a11y-utils.js');
const a11yUtils = await import(A11Y_UTILS_PATH);

// 脚本运行在 scripts/ 目录下，依赖安装在 micro-course-admin/node_modules/
const ADMIN_NM = resolve(PROJECT_ROOT, 'micro-course-admin/node_modules');
const _require = createRequire(ADMIN_NM);
const { chromium, firefox, webkit } = _require('playwright');

// @axe-core/playwright v4+: 用 createRequire 加载 CJS 版本
// createRequire 能正确处理 ESM ↔ CJS 的类导出
const AxeBuilderClass = _require('@axe-core/playwright');

// ============================================================
// 0. 环境变量 & 常量
// ============================================================

const {
  AUDIT_BASE_URL = 'http://localhost:8088',
  AUDIT_USERNAME = 'teacher1',
  AUDIT_PASSWORD = 'password123',
  AUDIT_BROWSER = 'chromium',
  AUDIT_HEADLESS = 'true',
  AUDIT_OUTPUT_DIR = 'docs/audit/teacher-module-audit',
  AUDIT_SKIP_A11Y = 'false',
} = process.env;

const HEADLESS = AUDIT_HEADLESS !== 'false';
const SKIP_A11Y = AUDIT_SKIP_A11Y === 'true';

// 加载 a11y 基线
const baseline = a11yUtils.loadBaseline(resolve(PROJECT_ROOT, 'micro-course-admin/e2e/a11y-baseline.json'));

// ============================================================
// 1. 生产环境阻断
// ============================================================
const PRODUCTION_HOSTS = [
  '100.74.122.13',
  'microcourse.ailyedu.cn',
  'microcourse.ailyun.cn',
];

function rejectProductionHost(url) {
  try {
    const u = new URL(url);
    for (const host of PRODUCTION_HOSTS) {
      if (u.hostname === host || u.hostname.endsWith('.' + host)) {
        console.error(`\n🚨 [FATAL] 检测到生产目标: ${url}`);
        console.error('   本脚本仅允许 localhost/127.0.0.1/0.0.0.0');
        console.error('   生产验证请走灰度发布流程: scripts/gray-release.sh');
        process.exit(3);
      }
    }
  } catch {
    console.error(`\n🚨 [FATAL] 无法解析 URL: ${url}`);
    process.exit(3);
  }
}

rejectProductionHost(AUDIT_BASE_URL);

// ============================================================
// 2. 路由清单（从 router/index.js 逐条提取，全量教师可达路由）
// ============================================================
// 每条路由标注:
//   - dynamic:true 含动态占位符 -> 需从测试数据解析真实 ID
//   - requiresLead:true -> 需微专业负责人权限；先通过 readonly API 尝试解析当前用户的
//     LEAD 微专业 ID，成功则实际访问；失败则标记 fixture 缺失并 exit 4
//   - roles -> 允许的角色列表
//
// 种子数据中的课程 ID 参考: local-dev-deploy.sh 会创建测试课程
// 这里使用已知的测试数据 ID，若 API 查询失败则标记 SKIPPED

const ROUTE_MANIFEST = [
  // ===== 教师专用路由（静态） =====
  { path: '/teacher/dashboard',                 name: '教师看板',           roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/courses',                   name: '教师课程列表',       roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/videos',                    name: '教师视频管理',       roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/exercises',                 name: '教师练习管理',       roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/discussions',               name: '教师答疑讨论',       roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/favorites',                 name: '教师收藏',           roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/questions',                 name: '教师题库',           roles: ['TEACHER'],         dynamic: false },
  // ===== 教师+管理员公用路由（静态） =====
  { path: '/teacher/students',                  name: '学员管理',           roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/grades',                    name: '成绩明细',           roles: ['TEACHER','ADMIN','ACADEMIC'], dynamic: false },
  { path: '/teacher/teaching-classes',          name: '教学班管理',         roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/profile',                   name: '教师个人设置',       roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/slides',                    name: '课件总览',           roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/exams',                     name: '试卷管理',           roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/offline-list',              name: '线下课管理',         roles: ['TEACHER','ADMIN'], dynamic: false },
  // ===== 共享路由（含 TEACHER 角色，静态） =====
  { path: '/courses/create',                    name: '课程创建(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/chapters',                          name: '章节管理(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/videos',                            name: '视频管理(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/exercises',                         name: '练习管理(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/discussions',                       name: '讨论管理(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/questions',                         name: '题库管理(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/notifications',                     name: '通知管理(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: false },
  { path: '/bundles',                           name: '课程套件(共享)',    roles: ['TEACHER','ADMIN','ACADEMIC'], dynamic: false },
  { path: '/profile',                           name: '个人资料(共享)',    roles: ['STUDENT','TEACHER','ADMIN','ACADEMIC'], dynamic: false },
  // ===== 微专业路由（静态） =====
  { path: '/teacher/micro-specialties',          name: '微专业管理',       roles: ['TEACHER','ADMIN'], dynamic: false },
  { path: '/teacher/micro-specialties/invites',  name: '微专业邀请',       roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/micro-specialties/proposals',name: '微专业申报',       roles: ['TEACHER'],         dynamic: false },
  { path: '/teacher/micro-specialties/my-proposals', name: '我的申报',    roles: ['TEACHER'],         dynamic: false },
  // ===== 学生端-教师共享路由（TEACHER 可通过 meta.roles 访问） =====
  { path: '/student/learning',                  name: '学习中心(共享)',    roles: ['STUDENT','TEACHER','ADMIN'], dynamic: false },
  // ===== 动态路由（需解析真实 ID） =====
  { path: '/teacher/courses/:id',               name: '教师课程详情',       roles: ['TEACHER'],         dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/teacher/courses/:courseId/slides/manage', name: '课件管理',     roles: ['TEACHER','ADMIN'], dynamic: true, paramsMap: { courseId: 'courseId' } },
  { path: '/courses/:id',                       name: '课程详情(共享)',     roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/courses/:id/edit',                  name: '课程编辑(共享)',     roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/courses/:courseId/videos',           name: '课程视频(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: true, paramsMap: { courseId: 'courseId' } },
  { path: '/courses/:courseId/exercises',        name: '课程练习(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: true, paramsMap: { courseId: 'courseId' } },
  { path: '/courses/:courseId/exercises/form',   name: '练习表单(共享)',    roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: true, paramsMap: { courseId: 'courseId' } },
  { path: '/discussions/:id',                   name: '讨论详情(共享)',     roles: ['ADMIN','ACADEMIC','TEACHER'], dynamic: true, paramsMap: { id: 'discussionId' } },
  { path: '/teacher/courses/:courseId/chapters/:chapterId/manage-videos', name: '章节视频管理', roles: ['TEACHER','ADMIN'], dynamic: true, paramsMap: { courseId: 'courseId', chapterId: 'chapterId' } },
  { path: '/teacher/courses/:courseId/chapters/:chapterId/manage-slides', name: '章节课件管理', roles: ['TEACHER','ADMIN'], dynamic: true, paramsMap: { courseId: 'courseId', chapterId: 'chapterId' } },
  { path: '/teacher/courses/:courseId/chapters/:chapterId/manage-offline', name: '章节线下课', roles: ['TEACHER','ADMIN'], dynamic: true, paramsMap: { courseId: 'courseId', chapterId: 'chapterId' } },
  { path: '/teacher/courses/:courseId/chapters/:chapterId/manage-exam', name: '章节考试管理', roles: ['TEACHER','ADMIN'], dynamic: true, paramsMap: { courseId: 'courseId', chapterId: 'chapterId' } },
  { path: '/teacher/chapters/:chapterId/offline-sessions', name: '线下课场次', roles: ['TEACHER','ADMIN'], dynamic: true, paramsMap: { chapterId: 'chapterId' } },
  // ===== 学生端-教师共享动态路由 =====
  { path: '/student/courses/:id',               name: '学生课程详情(教师可访)', roles: ['STUDENT','TEACHER','ADMIN'], dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/student/courses/:id/play/:videoId?',name: '学生视频播放(教师可访)', roles: ['STUDENT','TEACHER','ADMIN'], dynamic: true, paramsMap: { id: 'courseId' } },
  { path: '/student/chapters/:chapterId/exercises', name: '学生章节练习(教师可访)', roles: ['STUDENT','TEACHER','ADMIN'], dynamic: true, paramsMap: { chapterId: 'chapterId' } },
  { path: '/student/courses/:courseId/slides/player', name: '学生端PPT播放(教师可访)', roles: ['STUDENT','TEACHER','ADMIN'], dynamic: true, paramsMap: { courseId: 'courseId' } },
  { path: '/student/chapters/:chapterId/offline', name: '学生端线下课(教师可访)', roles: ['STUDENT','TEACHER','ADMIN'], dynamic: true, paramsMap: { chapterId: 'chapterId' } },
  { path: '/student/micro-specialties/:id',      name: '微专业详情(教师可访)', roles: ['STUDENT','TEACHER','ACADEMIC','ADMIN'], dynamic: true, paramsMap: { id: 'microSpecialtyId' } },
  // ===== 微专业动态路由（requiresLead，需负责人权限） =====
  { path: '/teacher/micro-specialties/:id/manage',  name: '微专业工作台',    roles: ['TEACHER','ADMIN'], dynamic: true, requiresLead: true, paramsMap: { id: 'microSpecialtyId' } },
  { path: '/teacher/micro-specialties/:id/courses', name: '微专业课程编排',  roles: ['TEACHER','ADMIN'], dynamic: true, requiresLead: true, paramsMap: { id: 'microSpecialtyId' } },
  { path: '/teacher/micro-specialties/:id/team',    name: '微专业团队管理',  roles: ['TEACHER','ADMIN'], dynamic: true, requiresLead: true, paramsMap: { id: 'microSpecialtyId' } },
  { path: '/teacher/micro-specialties/storage-preview/:id', name: '存储申请表预览', roles: ['TEACHER','ACADEMIC'], dynamic: true, paramsMap: { id: 'storageAppId' } },
];

// ============================================================
// 3. 辅助函数
// ============================================================

/**
 * 从路由路径中提取动态参数名（如 /courses/:id => ['id']）
 */
function extractDynamicParams(path) {
  const params = [];
  const segments = path.split('/');
  for (const seg of segments) {
    if (seg.startsWith(':')) {
      params.push(seg.slice(1).replace(/\?$/, ''));
    }
  }
  return params;
}

/**
 * 将动态路由路径用实际值填充
 */
function fillDynamicPath(path, values) {
  let result = path;
  for (const [key, val] of Object.entries(values)) {
    result = result.replace(`:${key}`, val);
    result = result.replace(`:${key}?`, val);
  }
  return result;
}

/**
 * 将网络错误按状态码分类
 * @param {Array<{status: number, url: string}>} errors
 * @returns {{ server5xx: Array, client4xx: Array, expected4xx: Array }}
 */
function classifyNetworkErrors(errors) {
  const server5xx = [];
  const client4xx = [];
  const expected4xx = [];

  for (const e of errors) {
    if (e.status >= 500) {
      server5xx.push(e);
    } else if (e.status === 403 || e.status === 404) {
      // 403/404 可以是预期行为（权限不足/资源不存在）
      // 标记为 expected，但如果是页面主资源失败则需要升级
      expected4xx.push(e);
    } else if (e.status >= 400) {
      client4xx.push(e);
    }
  }

  return { server5xx, client4xx, expected4xx };
}

// ============================================================
// 4. 主流程
// ============================================================

const AUDIT_DIR = resolve(PROJECT_ROOT, AUDIT_OUTPUT_DIR);
const TIMESTAMP = new Date().toISOString().replace(/[:.]/g, '-');
const REPORT_FILE = resolve(AUDIT_DIR, `audit-report-${TIMESTAMP}.json`);
const SUMMARY_FILE = resolve(AUDIT_DIR, `audit-summary-${TIMESTAMP}.json`);

if (!existsSync(AUDIT_DIR)) {
  mkdirSync(AUDIT_DIR, { recursive: true });
}

/**
 * 从 API 尝试解析动态路由所需的各类 ID（只读，不写业务数据）
 *
 * v5 变更: 修正 API 路径
 *   - /api/discussions → /api/discussions/posts
 *   - /api/micro-specialties/storage-applications → /api/storage-applications/my-drafts
 * @returns {Promise<{courseId: string|null, chapterId: string|null, discussionId: string|null, microSpecialtyId: string|null, storageAppId: string|null}>}
 */
async function resolveDynamicIds(page) {
  /** @type {{courseId: string|null, chapterId: string|null, discussionId: string|null, microSpecialtyId: string|null, storageAppId: string|null}} */
  const ids = { courseId: null, chapterId: null, discussionId: null, microSpecialtyId: null, storageAppId: null };

  // ---- Course ID ----
  try {
    const resp = await page.request.get(`${AUDIT_BASE_URL}/api/courses?page=0&size=1`);
    if (resp.ok()) {
      const body = await resp.json();
      const items = body.data?.items || body.items || [];
      if (items.length > 0) ids.courseId = String(items[0].id);
    }
  } catch { /* 静默 */ }

  // ---- Chapter ID（从第一门课程获取章节列表） ----
  if (ids.courseId) {
    try {
      const resp = await page.request.get(`${AUDIT_BASE_URL}/api/courses/${ids.courseId}/chapters?page=0&size=1`);
      if (resp.ok()) {
        const body = await resp.json();
        const items = body.data?.items || body.items || [];
        if (items.length > 0) ids.chapterId = String(items[0].id);
      }
    } catch { /* 静默 */ }
  }

  // ---- Discussion ID ----
  // 修正路径: 实际端点 /api/discussions/posts（非 /api/discussions）
  try {
    const resp = await page.request.get(`${AUDIT_BASE_URL}/api/discussions/posts?page=0&size=1`);
    if (resp.ok()) {
      const body = await resp.json();
      const items = body.data?.items || body.items || [];
      if (items.length > 0) ids.discussionId = String(items[0].id);
    }
  } catch { /* 静默 */ }

  // ---- Micro-specialty ID（任意） ----
  try {
    const resp = await page.request.get(`${AUDIT_BASE_URL}/api/micro-specialties?page=0&size=1`);
    if (resp.ok()) {
      const body = await resp.json();
      const items = body.data?.items || body.items || [];
      if (items.length > 0) ids.microSpecialtyId = String(items[0].id);
    }
  } catch { /* 静默 */ }

  // ---- Storage Application ID ----
  // 修正路径: 实际端点 /api/storage-applications/my-drafts（非 /api/micro-specialties/storage-applications）
  try {
    const resp = await page.request.get(`${AUDIT_BASE_URL}/api/storage-applications/my-drafts?page=0&size=1`);
    if (resp.ok()) {
      const body = await resp.json();
      const items = body.data?.items || body.items || [];
      if (items.length > 0) ids.storageAppId = String(items[0].id);
    }
  } catch { /* 静默 */ }

  return ids;
}

/**
 * 从 API 尝试解析当前用户为 LEAD 的微专业 ID（只读）
 * 用于 requiresLead 路由的实际访问
 * @returns {Promise<string|null>}
 */
async function resolveLeadMicroSpecialtyId(page) {
  // 方案 1: 直接查询 LEAD 专用端点
  try {
    const resp = await page.request.get(`${AUDIT_BASE_URL}/api/micro-specialties/lead`);
    if (resp.ok()) {
      const body = await resp.json();
      const items = body.data?.items || body.items || [];
      if (items.length > 0) return String(items[0].id);
    }
  } catch { /* 静默 */ }

  // 方案 2: 遍历微专业列表检查 my-role
  try {
    const resp = await page.request.get(`${AUDIT_BASE_URL}/api/micro-specialties?page=0&size=20`);
    if (resp.ok()) {
      const body = await resp.json();
      const items = body.data?.items || body.items || [];
      for (const ms of items) {
        if (!ms.id) continue;
        try {
          const roleResp = await page.request.get(`${AUDIT_BASE_URL}/api/micro-specialties/${ms.id}/my-role`);
          if (roleResp.ok()) {
            const roleBody = await roleResp.json();
            if (roleBody.data?.role === 'LEAD') return String(ms.id);
          }
        } catch { /* 单条失败跳过 */ }
      }
    }
  } catch { /* 静默 */ }

  return null;
}

async function runAudit() {
  // 选择浏览器
  let browserType;
  let browserLabel;
  switch (AUDIT_BROWSER) {
    case 'firefox': browserType = firefox; browserLabel = 'Firefox'; break;
    case 'webkit':  browserType = webkit;  browserLabel = 'WebKit (Safari兼容代理)'; break;
    default:        browserType = chromium; browserLabel = 'Chromium'; break;
  }

  const viewport = { width: 1440, height: 900 };

  console.log(`\n🧪 教师页面批量回归审计 v4`);
  console.log(`   ├─ 目标: ${AUDIT_BASE_URL}`);
  console.log(`   ├─ 用户: ${AUDIT_USERNAME}`);
  console.log(`   ├─ 浏览器: ${browserLabel}`);
  console.log(`   ├─ Viewport: ${viewport.width}x${viewport.height}`);
  console.log(`   ├─ Headless: ${HEADLESS}`);
  console.log(`   ├─ a11y: ${SKIP_A11Y ? '跳过' : 'axe-core + 基线豁免'}`);
  console.log(`   ├─ 路由数: ${ROUTE_MANIFEST.length}`);
  console.log(`   ├─ 环境: ${platform()} ${arch()}, ${type()} ${release()}`);
  console.log(`   └─ 报告: ${AUDIT_DIR}/\n`);

  const browser = await browserType.launch({ headless: HEADLESS });
  const context = await browser.newContext({
    viewport,
    ignoreHTTPSErrors: true,
    locale: 'zh-CN',
    userAgent: AUDIT_BROWSER === 'webkit'
      ? 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) (Playwright WebKit — Safari兼容代理，非真实Safari)'
      : undefined,
  });
  const page = await context.newPage();

  // ---------- 全局捕获 ----------
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.debug(`  [console.error] ${msg.text().slice(0, 120)}`);
    }
  });

  // ---------- 登录 ----------
  console.log('[阶段 1/3] 登录教师账号...');
  await page.goto(`${AUDIT_BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForLoadState('domcontentloaded');

  // 兼容项目实际 login 表单:input 用 placeholder 而非 id 定位
  await page.waitForSelector('input[placeholder*="用户"], input[type="text"]', { timeout: 10000 });
  await page.waitForSelector('input[type="password"]', { timeout: 10000 });
  const usernameInput = page.locator('input[placeholder*="用户"], input[type="text"]').first();
  const passwordInput = page.locator('input[type="password"]').first();
  await usernameInput.fill(AUDIT_USERNAME);
  await passwordInput.fill(AUDIT_PASSWORD);

  // 主选择器 .login-btn + 文本回退（应对可能的不同按钮文案）
  const loginBtn = page.locator('.login-btn');
  const loginBtnAlt = page.locator('button:has-text("登 录"), button:has-text("登录")');
  if (await loginBtn.isVisible().catch(() => false)) {
    await loginBtn.click();
  } else {
    await loginBtnAlt.first().click();
  }
  await page.waitForURL('**/teacher/dashboard', { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  console.log('   ✓ 登录成功 → /teacher/dashboard\n');

  // ---------- 尝试解析动态路由参数（只读 API，不写业务数据） ----------
  const dynamicIds = await resolveDynamicIds(page);
  console.log(`   [信息] 动态 ID 解析结果:`);
  console.log(`      courseId:       ${dynamicIds.courseId || '未获取'}`);
  console.log(`      chapterId:      ${dynamicIds.chapterId || '未获取'}`);
  console.log(`      discussionId:   ${dynamicIds.discussionId || '未获取'}`);
  console.log(`      microSpecialtyId: ${dynamicIds.microSpecialtyId || '未获取'}`);
  console.log(`      storageAppId:   ${dynamicIds.storageAppId || '未获取'}`);

  // ---------- 尝试解析当前用户的 LEAD 微专业 ID（用于 requiresLead 路由） ----------
  const leadMsId = await resolveLeadMicroSpecialtyId(page);
  if (leadMsId) {
    console.log(`   [信息] 当前用户有 LEAD 微专业 ID: ${leadMsId}`);
  } else {
    console.log('   [信息] 未找到当前用户的 LEAD 微专业，requiresLead 路由将标记 SKIPPED（fixture 缺失）');
  }

  // ---------- 逐页审查 ----------
  console.log('[阶段 2/3] 逐页审查...\n');

  const results = [];
  let p0Count = 0;
  let p1cCount = 0;
  const skippedEntries = [];   // 专门记录 SKIPPED 信息

  const totalRoutes = ROUTE_MANIFEST.length;

  for (let i = 0; i < totalRoutes; i++) {
    const route = ROUTE_MANIFEST[i];
    const entry = {
      index: i + 1,
      path: route.path,
      name: route.name,
      roles: route.roles,
      status: 'ok',
      issues: [],
      a11y: { violations: 0, passes: 0, incomplete: 0, blocked: 0, exempted: 0 },
      timing: null,
      screenshot: null,
      consoleErrors: [],
      networkErrors: [],
    };

    // 处理动态路由（含 requiresLead）
    let effectivePath = route.path;
    let skipReason = null;
    if (route.dynamic) {
      if (route.requiresLead) {
        if (leadMsId) {
          // 用 LEAD 微专业 ID + 通用 ID 填充
          const fillValues = {
            id: leadMsId,
            courseId: dynamicIds.courseId || '',
            chapterId: dynamicIds.chapterId || '',
            microSpecialtyId: leadMsId,
            storageAppId: '',
          };
          if (route.paramsMap) {
            for (const [rp, src] of Object.entries(route.paramsMap)) {
              if (fillValues[rp] !== undefined) continue; // 显式已设的不覆盖
              fillValues[rp] = dynamicIds[src] || '';
            }
          }
          effectivePath = fillDynamicPath(route.path, fillValues);
          const remainingParams = extractDynamicParams(effectivePath);
          if (remainingParams.length > 0) {
            skipReason = `SKIPPED: 仍有未解析的动态参数 (${remainingParams.join(', ')}) - ${effectivePath}`;
          }
        } else {
          skipReason = `SKIPPED: fixture缺失 — 当前用户无 LEAD 微专业且 API 无可用数据`;
        }
      } else {
        // 通用动态路由：按 paramsMap 填充
        const fillValues = {};
        // 先填后备映射（兼容无 paramsMap 的旧路由）
        fillValues.id = dynamicIds.courseId || '';
        fillValues.courseId = dynamicIds.courseId || '';
        fillValues.chapterId = dynamicIds.chapterId || '';
        fillValues.discussionId = dynamicIds.discussionId || '';
        fillValues.microSpecialtyId = dynamicIds.microSpecialtyId || '';
        fillValues.storageAppId = dynamicIds.storageAppId || '';
        // paramsMap 覆盖
        if (route.paramsMap) {
          for (const [rp, src] of Object.entries(route.paramsMap)) {
            fillValues[rp] = dynamicIds[src] || '';
          }
        }
        effectivePath = fillDynamicPath(route.path, fillValues);

        // 检查是否还有未填充的必须参数（跳过 optional params）
        const allParams = extractDynamicParams(route.path);
        const missingParams = [];
        for (const p of allParams) {
          // 检查路径中是否还有没被替换的 :param
          if (effectivePath.includes(`:${p}`) || effectivePath.includes(`:${p}?`)) {
            if (!p.endsWith('?')) missingParams.push(p);
          }
        }
        if (missingParams.length > 0) {
          skipReason = `SKIPPED: 无法解析动态参数 (${missingParams.join(', ')})`;
        }
      }

      if (skipReason) {
        entry.status = 'skipped';
        entry.issues.push(skipReason);
        entry.dynamicResolved = false;
        entry.skipReason = skipReason;
        skippedEntries.push({ name: route.name, path: route.path, reason: skipReason });
        results.push(entry);
        continue;
      }
      console.log(`   [动态] ${route.path} → ${effectivePath}`);
    }

    try {
      const logPrefix = `  [${i + 1}/${totalRoutes}] ${route.name}`;
      console.log(`  ── ${logPrefix} (${effectivePath}) ──`);

      // ---- 页级捕获隔离 ----
      const pageConsoleErrors = [];
      const pageNetworkErrors = [];

      const consoleHandler = msg => {
        if (msg.type() === 'error') {
          pageConsoleErrors.push({ text: msg.text().slice(0, 200), url: page.url() });
        }
      };
      const responseHandler = async resp => {
        if (!resp.ok() && resp.url().startsWith(AUDIT_BASE_URL)) {
          pageNetworkErrors.push({
            url: resp.url().replace(AUDIT_BASE_URL, ''),
            status: resp.status(),
          });
        }
      };

      page.on('console', consoleHandler);
      page.on('response', responseHandler);

      // ---- 导航 ----
      const navStart = performance.now();
      await page.goto(`${AUDIT_BASE_URL}${effectivePath}`, {
        waitUntil: 'networkidle',
        timeout: 25000,
      });
      // 等待异步渲染
      await page.waitForTimeout(1500);
      const navEnd = performance.now();

      // ---- 页面状态检查 ----
      const currentUrl = page.url();
      const bodyText = await page.textContent('body').catch(() => '');

      // 权限重定向检查
      if (currentUrl.includes('/login')) {
        entry.status = 'auth-fail';
        entry.issues.push('P0: 页面重定向到登录页 - 权限控制或 Token 失效');
        // 尝试重新登录（用同样的 fallback 模式）
        await page.goto(`${AUDIT_BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
        await page.waitForSelector('input[placeholder*="用户"], input[type="text"]', { timeout: 10000 });
        const reloginUser = page.locator('input[placeholder*="用户"], input[type="text"]').first();
        const reloginPwd = page.locator('input[type="password"]').first();
        await reloginUser.fill(AUDIT_USERNAME);
        await reloginPwd.fill(AUDIT_PASSWORD);
        const reloginBtn = page.locator('.login-btn');
        const reloginBtnAlt = page.locator('button:has-text("登 录"), button:has-text("登录")');
        if (await reloginBtn.isVisible().catch(() => false)) {
          await reloginBtn.click();
        } else {
          await reloginBtnAlt.first().click();
        }
        await page.waitForURL('**/teacher/dashboard', { timeout: 15000 });
        await page.waitForLoadState('networkidle');
      } else {
        // 404 / 403 检查
        // 注意：只检测真正的 404 页面（如 SPA 的 NotFound 组件渲染），
        // 不匹配 API 错误 toast 中的 "404"（如 "获取 API Key 失败: Request failed with status code 404"）
        const hasPage404 = /(页面不存在|找不到页面|404\s*Not Found)/i.test(bodyText);
        const hasForbidden = /(无权访问|没有权限|403\s*Forbidden)/i.test(bodyText);
        if (hasPage404) {
          entry.status = 'not-found';
          entry.issues.push('P0: 页面返回 404');
        } else if (hasForbidden) {
          entry.status = 'forbidden';
          entry.issues.push('P1-C: 页面提示无权访问（可能是预期行为）');
        }
      }

      // ---- 性能指标 ----
      const perfMetrics = await page.evaluate(() => {
        const nav = performance.getEntriesByType('navigation')[0];
        if (!nav) return null;
        const paintEntries = performance.getEntriesByType('paint');
        return {
          domContentLoaded: Math.round(nav.domContentLoadedEventEnd - nav.fetchStart),
          loadComplete: Math.round(nav.loadEventEnd - nav.fetchStart),
          domInteractive: Math.round(nav.domInteractive - nav.fetchStart),
          firstContentfulPaint: paintEntries.find(p => p.name === 'first-contentful-paint')?.startTime
            ? Math.round(paintEntries.find(p => p.name === 'first-contentful-paint').startTime)
            : null,
          navigationType: nav.type,
          transferSize: nav.transferSize,
        };
      });
      entry.timing = {
        ...perfMetrics,
        totalWallMs: Math.round(navEnd - navStart),
      };

      // ---- 性能基线检查 ----
      if (perfMetrics) {
        if (perfMetrics.loadComplete > 3000) {
          entry.issues.push(`P2: 完全加载 ${perfMetrics.loadComplete}ms > 3s 基线`);
        }
        if (perfMetrics.firstContentfulPaint !== null && perfMetrics.firstContentfulPaint > 2000) {
          entry.issues.push(`P2: FCP ${perfMetrics.firstContentfulPaint}ms > 2s 基线`);
        }
      }

      // ---- 截图（fullPage） ----
      const safeName = `${String(i + 1).padStart(2, '0')}_${route.name.replace(/[\/()\s]/g, '_')}`;
      const screenshotPath = resolve(AUDIT_DIR, `${safeName}.png`);
      await page.screenshot({ path: screenshotPath, fullPage: true });
      entry.screenshot = basename(screenshotPath);

      // ---- Console & Network 错误（分类处理） ----
      if (pageConsoleErrors.length > 0) {
        entry.issues.push(`P1-C: ${pageConsoleErrors.length} 个控制台错误`);
      }
      entry.consoleErrors = pageConsoleErrors;

      // 网络错误分类
      const { server5xx, client4xx, expected4xx } = classifyNetworkErrors(pageNetworkErrors);
      entry.networkErrors = pageNetworkErrors;
      entry.networkClassification = {
        server5xx: server5xx.length,
        client4xx: client4xx.length,
        expected4xx: expected4xx.length,
      };

      if (server5xx.length > 0) {
        entry.issues.push(`P0: ${server5xx.length} 个 5xx 服务端错误`);
        server5xx.forEach(e => {
          entry.issues.push(`  P0: 5xx ${e.status} ${e.url}`);
        });
      }
      if (client4xx.length > 0) {
        entry.issues.push(`P1-C: ${client4xx.length} 个客户端错误 (非 403/404)`);
      }
      if (expected4xx.length > 0) {
        // 预期 403/404 仅记录，不作为缺陷
        entry.issues.push(`P2: ${expected4xx.length} 个 403/404（可能是预期行为）`);
      }

      // ---- a11y 扫描 (axe-core) ----
      if (!SKIP_A11Y) {
        try {
          const axeResults = await new AxeBuilderClass({ page })
            .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
            .analyze();

          // 基线过滤
          const { remaining, removed } = a11yUtils.filterBaselineViolations(axeResults.violations, baseline);

          // 严重程度分类
          const gate = a11yUtils.shouldBlock(remaining);
          const severityMap = a11yUtils.classifyViolations(remaining);

          entry.a11y = {
            rawViolations: axeResults.violations.length,
            exempted: removed.length,
            violations: remaining.length,
            blocked: gate.blocks,
            passes: axeResults.passes.length,
            incomplete: axeResults.incomplete.length,
            bySeverity: {
              critical: severityMap.critical.length,
              serious: severityMap.serious.length,
              moderate: severityMap.moderate.length,
              minor: severityMap.minor.length,
            },
            blockingDetails: remaining.filter(v => v.impact === 'critical' || v.impact === 'serious').map(v => ({
              id: v.id,
              impact: v.impact,
              description: v.description,
              count: v.nodes.length,
            })),
            allDetails: remaining.map(v => ({
              id: v.id,
              impact: v.impact,
              description: v.description,
              count: v.nodes.length,
            })),
          };

          // 按 severity 记录 issue
          if (gate.critical > 0) {
            entry.issues.push(`P0: ${gate.critical} 个 critical axe 违规`);
          }
          if (gate.serious > 0) {
            entry.issues.push(`P1-C: ${gate.serious} 个 serious axe 违规`);
          }
          if (gate.moderate > 0) {
            entry.issues.push(`P2: ${gate.moderate} 个 moderate axe 违规`);
          }
          if (gate.minor > 0) {
            entry.issues.push(`P2: ${gate.minor} 个 minor axe 违规`);
          }
        } catch (axeErr) {
          console.warn(`     ⚠ axe 扫描失败: ${axeErr.message.slice(0, 100)}`);
          entry.a11y.error = axeErr.message.slice(0, 200);
        }
      }

      // ---- 统计 ----
      for (const issue of entry.issues) {
        if (issue.startsWith('P0:')) p0Count++;
        else if (issue.startsWith('P1-C:')) p1cCount++;
      }

      results.push(entry);
      console.log(`     → 状态: ${entry.status} | ` +
        `console:${pageConsoleErrors.length} net:${pageNetworkErrors.length} ` +
        `(5xx:${server5xx.length} 4xx:${client4xx.length} 403/404:${expected4xx.length}) ` +
        `a11y:${!SKIP_A11Y && entry.a11y ? `raw=${entry.a11y.rawViolations} blk=${entry.a11y.blocked}` : '-'} | ` +
        `问题: ${entry.issues.length}`);

      // ---- 清理页级监听 ----
      page.removeListener('console', consoleHandler);
      page.removeListener('response', responseHandler);

    } catch (err) {
      entry.status = 'error';
      entry.issues.push(`P0: 页面加载异常 - ${err.message.slice(0, 150)}`);
      p0Count++;
      results.push(entry);
      console.log(`     → ERROR: ${err.message.slice(0, 80)}`);
    }
  }

  // ---------- 生成报告 ----------
  console.log('\n[阶段 3/3] 生成审查报告...');

  const routeSummary = {
    total: results.length,
    ok: results.filter(r => r.status === 'ok').length,
    error: results.filter(r => r.status === 'error').length,
    authFail: results.filter(r => r.status === 'auth-fail').length,
    notFound: results.filter(r => r.status === 'not-found').length,
    forbidden: results.filter(r => r.status === 'forbidden').length,
    skipped: results.filter(r => r.status === 'skipped').length,
  };

  // 覆盖率计算
  const auditable = routeSummary.total - routeSummary.skipped;
  const successfullyAudited = routeSummary.ok;
  const coverage = auditable > 0
    ? Math.round((successfullyAudited / auditable) * 10000) / 100
    : 0;
  const hasCoverageGap = routeSummary.skipped > 0;

  // a11y 全量汇总
  const allAxeBlocked = results.reduce((sum, r) => {
    return sum + (r.a11y?.blocked || 0);
  }, 0);
  const allAxeCritical = results.reduce((sum, r) => {
    return sum + (r.a11y?.bySeverity?.critical || 0);
  }, 0);
  const allAxeSerious = results.reduce((sum, r) => {
    return sum + (r.a11y?.bySeverity?.serious || 0);
  }, 0);

  const report = {
    meta: {
      timestamp: TIMESTAMP,
      baseUrl: AUDIT_BASE_URL,
      username: AUDIT_USERNAME,
      browser: browserLabel,
      viewport: `${viewport.width}x${viewport.height}`,
      headless: HEADLESS,
      a11yEnabled: !SKIP_A11Y,
      a11yBaseline: 'e2e/a11y-baseline.json',
      tool: 'Playwright + @axe-core/playwright',
      complementaryTool: 'ego-browser（交互式主审查工具）',
      toolDivergence: 'Playwright → 可重复回归/CI门禁; ego-browser → 真实用户式走查/截图取证',
      prodGuard: '已拒绝生产 host',
      environment: {
        platform: platform(),
        arch: arch(),
        osType: type(),
        osRelease: release(),
        nodeVersion: process.version,
      },
    },
    coverage: {
      total: routeSummary.total,
      auditable,
      successfullyAudited,
      skipped: routeSummary.skipped,
      coveragePercent: coverage,
      hasCoverageGap,
      gapEntries: skippedEntries,
    },
    summary: routeSummary,
    defectCounts: {
      p0Issues: results.filter(r => r.issues.some(i => i.startsWith('P0'))).length,
      p1cIssues: results.filter(r => r.issues.some(i => i.startsWith('P1-C'))).length,
      p2Issues: results.filter(r => r.issues.some(i => i.startsWith('P2'))).length,
      a11yBlockedTotal: allAxeBlocked,
      a11yCriticalTotal: allAxeCritical,
      a11ySeriousTotal: allAxeSerious,
    },
    skippedDetails: skippedEntries,
    routes: results,
  };

  writeFileSync(REPORT_FILE, JSON.stringify(report, null, 2), 'utf-8');

  // 精简摘要
  const summary = {
    timestamp: TIMESTAMP,
    total: routeSummary.total,
    ok: routeSummary.ok,
    error: routeSummary.error,
    authFail: routeSummary.authFail,
    notFound: routeSummary.notFound,
    forbidden: routeSummary.forbidden,
    skipped: routeSummary.skipped,
    coverage,
    hasCoverageGap,
    affectedByP0: report.defectCounts.p0Issues,
    affectedByP1C: report.defectCounts.p1cIssues,
    affectedByP2: report.defectCounts.p2Issues,
    a11yBlockedTotal: allAxeBlocked,
  };
  writeFileSync(SUMMARY_FILE, JSON.stringify(summary, null, 2), 'utf-8');

  console.log(`\n📊 汇总:`);
  console.log(`   共 ${summary.total} 路由 | ✓ ${summary.ok} | ✗ ${summary.error} | 🔒 ${summary.authFail} | 404 ${summary.notFound} | ⛔ ${summary.forbidden} | ⏭ ${summary.skipped}`);
  console.log(`   覆盖率: ${coverage}% (可审计: ${auditable}, 成功: ${successfullyAudited}, 跳过: ${routeSummary.skipped})`);
  console.log(`   P0: ${summary.affectedByP0} 页 | P1-C: ${summary.affectedByP1C} 页 | P2: ${summary.affectedByP2} 页`);
  console.log(`   a11y 阻断: ${allAxeBlocked} (critical ${allAxeCritical}, serious ${allAxeSerious})`);

  if (skippedEntries.length > 0) {
    console.log(`\n   ⏭ 跳过路由 (${skippedEntries.length}):`);
    for (const s of skippedEntries) {
      console.log(`      ${s.name} (${s.path}): ${s.reason}`);
    }
  }

  console.log(`\n📁 报告: ${REPORT_FILE}`);
  console.log(`📁 摘要: ${SUMMARY_FILE}`);
  console.log(`🖼 截图: ${AUDIT_DIR}/`);

  // ---------- 清理 ----------
  await browser.close();

  // ---------- 退出码 ----------
  // 退出码优先级: P0(exit1) > P1-C(exit2) > 覆盖率不足(exit4,仅无缺陷时) > 全部通过(exit0)
  // 若同时有缺陷+skip: exit code 先按缺陷等级，报告中同时记录 coverage
  if (summary.affectedByP0 > 0) {
    console.log(`\n❌ 存在 P0 缺陷，退出码 1`);
    if (summary.hasCoverageGap) {
      console.log(`   ⚠ 同时覆盖率不足 (${coverage}%), 已在报告中记录`);
    }
    process.exit(1);
  }
  if (summary.affectedByP1C > 0) {
    console.log(`\n⚠️  存在 P1-C 缺陷，退出码 2`);
    if (summary.hasCoverageGap) {
      console.log(`   ⚠ 同时覆盖率不足 (${coverage}%), 已在报告中记录`);
    }
    process.exit(2);
  }
  if (summary.hasCoverageGap) {
    console.log(`\n⚠️  覆盖率不足 (${coverage}%), 退出码 4`);
    process.exit(4);
  }

  if (coverage === 100) {
    console.log(`\n✅ 全部通过，覆盖率 100%，退出码 0`);
  } else {
    // 理论上不应到达这里 (hasCoverageGap 已在上面 exit 4)
    console.log(`\n✅ 无缺陷，覆盖率 ${coverage}%，退出码 0`);
  }
  process.exit(0);
}

runAudit().catch(err => {
  console.error('\n🚨 审查脚本异常退出:', err);
  process.exit(3);
});
