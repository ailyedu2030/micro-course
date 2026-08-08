/**
 * Phase 14 跨浏览器 E2E 最终兜底验证 (Chromium / WebKit / Firefox)
 * =============================================================
 * 目标环境: localhost:5173 (vite dev 前端) → proxy localhost:8080 (dev 后端)
 * 数据: course 47 / chapter 71
 *   - section 4  (PPT v2): 3 页 (page 16/17/18)，page1 音频 FAILED，page3 无音频
 *   - section 50 (HTML v2): 3 段，无段级音频（段高亮/点击跳转仍须工作）
 * 账户: p0_teacher / student (password123)
 *
 * 覆盖:
 *   - PPT 完整流程: 教师登录 → 章节课时概览 → 预览(banner) → 讲述稿 → 学生播放/翻页/音频状态
 *   - HTML 段高亮 + 点击跳转: sandbox iframe 渲染 3 段 → 点击段 → 父页回环 → .active 高亮
 *   - 教师预览 banner: 教师打开学生播放器 URL 必须显示预览态标识
 *   - PPTX 上传 → 后台渲染 → 页面生成 (真实上传 journey)
 *
 * 生产保护: baseURL 仅 localhost (playwright.config.js 已阻断生产 host)
 */
import path from 'path';
import { fileURLToPath } from 'url';
import { test, expect } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080';
const COURSE_ID = 47;
const CHAPTER_ID = 71;
const SECTION_PPT = 4;
const SECTION_HTML = 50;
const TEACHER = { username: 'p0_teacher', password: 'password123' };
const STUDENT = { username: 'student', password: 'password123' };
const PPTX_FIXTURE = path.join(__dirname, 'fixtures', 'cross-browser-test.pptx');
const SHOTS = path.join(__dirname, 'screenshots', 'cross-browser');

test.describe.configure({ mode: 'parallel' });

async function apiLogin(user) {
  const resp = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(user),
  });
  const json = await resp.json();
  expect(json.code, `login ${user.username} failed: ${JSON.stringify(json)}`).toBe(200);
  return json.data.accessToken;
}

async function uiLogin(page, user, homeRe) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[id="username"]', { timeout: 30000 });
  await page.fill('input[id="username"]', user.username);
  await page.fill('input[id="password"]', user.password);
  await page.click('button.login-btn');
  await page.waitForURL(homeRe, { timeout: 30000 });
}

async function screenshot(page, name) {
  try {
    await page.screenshot({ path: path.join(SHOTS, name) });
  } catch { /* 截图失败不阻断测试 */ }
}

// 首次访问播放器会出现"键盘操作提示"遮罩 dialog，会拦截点击 → 测试前必须关闭。
// 竞态处理：hint 在 onMounted 异步渲染，先 wait visible（最多 10s）再点击关闭。
async function dismissKeyboardHint(page) {
  try {
    await page.locator('.keyboard-hint').waitFor({ state: 'visible', timeout: 10000 });
    await page.locator('.keyboard-hint-dismiss').click({ timeout: 5000 });
    await page.locator('.keyboard-hint').waitFor({ state: 'detached', timeout: 5000 });
  } catch { /* hint 未出现则无需处理 */ }
}

const browsers = [
  ['Chromium', 'chromium'],
  ['WebKit', 'webkit'],
  ['Firefox', 'firefox'],
];

// ===========================================================================
// 1. PPT 课件完整流程（教师管理/预览/讲述稿 + 学生播放/翻页/音频状态）
// ===========================================================================
for (const [label] of browsers) {
  test(`${label}: PPT 课件完整流程`, async ({ browser }) => {
    const bname = browser.browserType().name();
    const tctx = await browser.newContext();
    const tpage = await tctx.newPage();
    tpage.setDefaultTimeout(30000);

    // 教师登录
    await uiLogin(tpage, TEACHER, /\/teacher\/(dashboard|courses)/);
    // 章节课时概览
    await tpage.goto(`${BASE}/teacher/courses/${COURSE_ID}/chapters/${CHAPTER_ID}/manage-slides`, { waitUntil: 'domcontentloaded' });
    await tpage.waitForSelector('.sm-co-table .el-table__row', { timeout: 30000 });
    const pptRow = tpage.locator('.sm-co-table .el-table__row', { hasText: '章节一' });
    await expect(pptRow).toBeVisible({ timeout: 30000 });

    // 预览 → 全屏 dialog → 预览 banner + 播放器渲染 1/3
    const previewBtn = pptRow.getByRole('button', { name: '预览' });
    await expect(previewBtn).toBeEnabled();
    await previewBtn.click();
    await tpage.waitForSelector('.teacher-preview-banner', { timeout: 30000 });
    await expect(tpage.locator('.tpb-text')).toContainText('教师预览模式', { timeout: 10000 });
    await expect(tpage.locator('.page-counter')).toContainText('1/3', { timeout: 40000 });
    await dismissKeyboardHint(tpage);
    await screenshot(tpage, `ppt-preview-banner-${bname}.png`);
    await tpage.locator('.tpb-exit').click();
    await tpage.waitForSelector('.teacher-preview-banner', { state: 'detached', timeout: 30000 });

    // 管理课件 → 课时级 PPT 管理 → 页面列表 + 讲述稿
    await tpage.locator('.sm-co-table .el-table__row', { hasText: '章节一' }).getByRole('button', { name: '管理课件' }).click();
    await tpage.waitForURL(new RegExp(`slides/manage\\?sectionId=${SECTION_PPT}`), { timeout: 30000 });
    await expect(tpage.locator('.pcm-page-radio', { hasText: '第 1 页' })).toBeVisible({ timeout: 40000 });
    await expect(tpage.locator('.pcm-page-radio', { hasText: '第 3 页' })).toBeVisible({ timeout: 10000 });
    await tpage.getByRole('tab', { name: '讲述稿' }).click();
    const scriptTa = tpage.locator('textarea[placeholder*="输入讲述稿内容"]');
    await expect(scriptTa).toBeVisible({ timeout: 20000 });
    await expect(scriptTa).toHaveValue(/E2E 测试讲述稿/, { timeout: 20000 });
    await screenshot(tpage, `ppt-script-editor-${bname}.png`);
    await tctx.close();

    // 学生播放
    const sctx = await browser.newContext();
    const spage = await sctx.newPage();
    spage.setDefaultTimeout(30000);
    await uiLogin(spage, STUDENT, /\/student\/courses/);
    await spage.goto(`${BASE}/student/courses/${COURSE_ID}/slides/player?sectionId=${SECTION_PPT}`, { waitUntil: 'domcontentloaded' });
    await expect(spage.locator('.page-counter')).toContainText('1/3', { timeout: 40000 });
    await dismissKeyboardHint(spage);
    // page1 音频: 无 READY 音频 → 播放器展示失败/错误态 + 重试入口（三引擎一致）
    await expect(spage.locator('.audio-status')).toContainText('音频', { timeout: 30000 });
    const hasRetry = await spage.locator('.audio-status').getByRole('button').count();
    expect(hasRetry, '失败/错误态必须有重试入口').toBeGreaterThanOrEqual(1);
    // 翻页 2 → 3
    await spage.locator('.ctrl-btn[aria-label="下一页"]').click();
    await expect(spage.locator('.page-counter')).toContainText('2/3', { timeout: 15000 });
    await spage.locator('.ctrl-btn[aria-label="下一页"]').click();
    await expect(spage.locator('.page-counter')).toContainText('3/3', { timeout: 15000 });
    // page3 无音频 → 必须仍渲染音频状态区（真实状态/错误态均诚实可见，不得空白）
    await expect(spage.locator('.audio-status')).toBeVisible({ timeout: 15000 });
    await screenshot(spage, `ppt-student-player-${bname}.png`);
    await sctx.close();
  });
}

// ===========================================================================
// 2. HTML 课件段高亮 + 点击跳转（sandbox iframe 渲染 3 段 → 点击 → .active 回环）
// ===========================================================================
for (const [label] of browsers) {
  test(`${label}: HTML 课件段高亮 + 点击跳转`, async ({ browser }) => {
    const bname = browser.browserType().name();
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    page.setDefaultTimeout(30000);
    await uiLogin(page, STUDENT, /\/student\/courses/);
    await page.goto(`${BASE}/student/courses/${COURSE_ID}/slides/player?sectionId=${SECTION_HTML}`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('iframe.slide-iframe', { timeout: 40000 });

    const frame = page.frameLocator('iframe.slide-iframe');
    await expect(frame.locator('[data-segment]').first()).toBeVisible({ timeout: 40000 });
    await dismissKeyboardHint(page);

    const segCount = await frame.locator('[data-segment]').count();
    expect(segCount, `段元素数量 >= 3 (实际 ${segCount})`).toBeGreaterThanOrEqual(3);

    // 1..3 段标记全部真实渲染
    for (let i = 1; i <= 3; i++) {
      await expect(frame.locator(`[data-segment="${i}"]`).first()).toBeVisible({ timeout: 15000 });
    }

    // 点击第 2 段 → 父页回环 → iframe 内 [data-segment="2"] 获得 .active 高亮
    await frame.locator('[data-segment="2"]').first().click();
    await expect(frame.locator('[data-segment="2"].active').first()).toBeVisible({ timeout: 15000 });
    await screenshot(page, `html-segment-highlight-${bname}.png`);
    await ctx.close();
  });
}

// ===========================================================================
// 3. 教师预览 banner（教师通过管理页"预览"打开播放器必须显示预览态标识）— 所有浏览器
//    （直接访问学生播放器 URL 会被角色守卫重定向回教师端，banner 唯一入口 = 预览 dialog）
// ===========================================================================
test('所有浏览器: 教师预览 banner 显示', async ({ browser }) => {
  const bname = browser.browserType().name();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  page.setDefaultTimeout(30000);
  await uiLogin(page, TEACHER, /\/teacher\/(dashboard|courses)/);
  await page.goto(`${BASE}/teacher/courses/${COURSE_ID}/chapters/${CHAPTER_ID}/manage-slides`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.sm-co-table .el-table__row', { timeout: 30000 });
  const pptRow = page.locator('.sm-co-table .el-table__row', { hasText: '章节一' });
  await pptRow.getByRole('button', { name: '预览' }).click();
  await expect(page.locator('.teacher-preview-banner')).toBeVisible({ timeout: 40000 });
  await expect(page.locator('.tpb-text')).toContainText('教师预览模式 · 不会记录学习进度', { timeout: 10000 });
  await expect(page.locator('.tpb-exit')).toBeVisible();
  await screenshot(page, `preview-banner-${bname}.png`);
  await ctx.close();
});

// ===========================================================================
// 4. PPTX 上传 → 后台渲染 → 页面生成（真实上传 journey）
// ===========================================================================
for (const [label] of browsers) {
  test(`${label}: PPTX 上传 → 渲染 → 页面生成`, async ({ browser }) => {
    const bname = browser.browserType().name();
    const token = await apiLogin(TEACHER);

    // 1) 新建独立课时（避免污染 section 4 已知数据）
    const ts = Date.now();
    const secRes = await fetch(`${API}/api/courses/${COURSE_ID}/chapters/${CHAPTER_ID}/sections`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: `Cross-Browser ${bname} ${ts}`, sectionType: 'INTERACTIVE', coursewareType: 'PPT', sortOrder: 90 }),
    });
    const secJson = await secRes.json();
    expect(secJson.code, `section create failed: ${JSON.stringify(secJson)}`).toBe(200);
    const sectionId = secJson.data.id;

    try {
      // 2) 教师登录 → 课时级管理 → 空状态创建二选一
      const ctx = await browser.newContext();
      const page = await ctx.newPage();
      page.setDefaultTimeout(30000);
      await uiLogin(page, TEACHER, /\/teacher\/(dashboard|courses)/);
      await page.goto(`${BASE}/teacher/courses/${COURSE_ID}/slides/manage?sectionId=${sectionId}`, { waitUntil: 'domcontentloaded' });
      const pptOption = page.locator('.sm-option', { hasText: 'PPT 课件' });
      await expect(pptOption).toBeVisible({ timeout: 30000 });
      const pptUpload = pptOption.locator('input[type="file"]');

      // 3) 真实上传 .pptx（el-upload 的 input 是隐藏的，直接 setInputFiles 即真实文件选择）
      await pptUpload.setInputFiles(PPTX_FIXTURE);

      // 4) 轮询渲染结果（与前端 startRenderPolling 同一 API）
      let type = '';
      let renderStatus = '';
      let pageCount = 0;
      for (let i = 0; i < 40; i++) {
        await page.waitForTimeout(3000);
        const tree = await fetch(`${API}/api/courses/${COURSE_ID}/courseware/tree?sectionId=${sectionId}`, {
          headers: { Authorization: `Bearer ${token}` },
        }).then((r) => r.json());
        const d = tree.data || {};
        type = d.type || '';
        renderStatus = d.renderStatus || '';
        pageCount = (d.pages || []).length;
        if (type === 'PPT' && pageCount > 0) break;
        if (renderStatus === 'FAILED') break;
      }
      expect(type, `上传渲染失败 renderStatus=${renderStatus} pages=${pageCount}（真实失败，非测试问题）`).toBe('PPT');
      expect(pageCount).toBeGreaterThanOrEqual(1);

      // 5) UI 刷新后进入 PPT 工作区并显示页面
      await page.reload({ waitUntil: 'domcontentloaded' });
      await expect(page.locator('.pcm-page-radio').first()).toBeVisible({ timeout: 40000 });
      await screenshot(page, `upload-render-${bname}.png`);
      await ctx.close();
    } finally {
      // 6) 清理：删除测试课时（force 移除课件）
      await fetch(`${API}/api/courses/${COURSE_ID}/chapters/${CHAPTER_ID}/sections/${sectionId}?force=true`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
    }
  });
}
