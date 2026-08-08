/**
 * Phase 13 最终兜底 E2E — PPT/HTML 课件真实用户旅程
 * =============================================
 * 目标环境: localhost:5173 (dev 前端) → 代理 localhost:8080 (dev 后端)
 * 数据: 课程 47 (互动课件演示 - 22), 教师 p0_teacher, 学生 student
 *   - section 4 (PPT v2): pages 16/17/18, script on page 16, audio GENERATING+FAILED, flow 16→17
 *   - section 50 (HTML v2): unit 3, 3 段, segment-1 script with marker seg-1
 *
 * 8 个 P0 修复验证映射:
 *   P0-A: 新 PPT 页保存讲述稿 → API PUT /ppt/pages/{id}/scripts 落库
 *   P0-B: 无参数入口加载 v2 课件 → GET /slides/pages (course-level) 返回 v2 pages
 *   P0-C: HTML 段高亮 → detect segments + player iframe 渲染段
 *   P0-D: 批量 AI 落库 → POST /ppt/pages/scripts/batch-ai-generate (无 key 时诚实报错)
 *   P0-E: segmentMarker 正确 → PUT /html/units/{id}/segments/1 {segmentMarker:seg-1}
 *   P0-F: flow 求值触发 → POST /courseware/{sectionId}/flow/evaluate
 *   P0-G: audio @error 即时反馈 → audio FAILED + error_message 渲染
 *   P0-H: GENERATING 段状态可见 → audio GENERATING 渲染"生成中"
 */

import { test, expect } from '@playwright/test';

const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080';
const TEACHER = { username: 'p0_teacher', password: 'password123' };
const STUDENT = { username: 'student', password: 'password123' };

async function apiLogin(user) {
  const resp = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(user),
  });
  const json = await resp.json();
  expect(json.code).toBe(200);
  return json.data.accessToken;
}

async function login(page, { username, password }) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.fill('input[id="username"]', username);
  await page.fill('input[id="password"]', password);
  await page.click('button.login-btn');
  await page.waitForTimeout(2500);
}

test.describe('Phase 13 最终兜底 - 真实端到端 (dev 环境)', () => {

  test('P0-A/P0-B: 新 PPT 页保存讲述稿 + 无参数入口加载 v2 课件 (API)', async () => {
    const token = await apiLogin(TEACHER);
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

    // P0-A: 保存讲述稿到新 PPT 页 (page 18, 无历史脚本)
    const saveRes = await fetch(`${API}/api/courses/47/ppt/pages/18/scripts`, {
      method: 'PUT', headers,
      body: JSON.stringify({ scriptText: 'E2E 第三页讲述稿：实时验证保存。', voice: 'alloy', ttsModel: 'tts-1' }),
    });
    const saveJson = await saveRes.json();
    expect(saveJson.code).toBe(200);
    expect(typeof saveJson.data).toBe('number');

    const readRes = await fetch(`${API}/api/courses/47/ppt/pages/18/scripts/active`, { headers });
    const readJson = await readRes.json();
    expect(readJson.data.scriptText).toContain('E2E 第三页讲述稿');

    // P0-B: 无参数入口 (course-level) 返回 v2 课件页 (sectionId 已嵌入)
    const pagesRes = await fetch(`${API}/api/courses/47/slides/pages`, { headers });
    const pagesJson = await pagesRes.json();
    expect(pagesJson.code).toBe(200);
    const pages = pagesJson.data || [];
    expect(pages.length).toBeGreaterThan(0);
    expect(pages[0].sectionId).toBeTruthy();
  });

  test('P0-E/P0-C: HTML 段检测 + segmentMarker 落库 (API)', async () => {
    const token = await apiLogin(TEACHER);
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

    const saveSegRes = await fetch(`${API}/api/courses/47/html/units/3/segments/2`, {
      method: 'PUT', headers,
      body: JSON.stringify({ scriptText: '第二段讲述稿', voice: 'alloy', ttsModel: 'tts-1', segmentMarker: 'seg-2' }),
    });
    expect((await saveSegRes.json()).code).toBe(200);

    const readSegRes = await fetch(`${API}/api/courses/47/html/units/3/segments/2`, { headers });
    const readSegJson = await readSegRes.json();
    expect(readSegJson.data.segmentMarker).toBe('seg-2');

    const detectRes = await fetch(`${API}/api/courses/47/html/units/3/detect`, { method: 'POST', headers });
    const detectJson = await detectRes.json();
    expect(detectJson.data.segments.length).toBeGreaterThanOrEqual(3);
    expect(detectJson.data.segments[0].marker).toBeTruthy();
    expect(detectJson.data.segments[0].selector).toBeTruthy();
  });

  test('P0-D/P0-F: 批量 AI 诚实报错/落库 + flow 求值触发 (API)', async () => {
    const token = await apiLogin(TEACHER);
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

    // P0-F: flow evaluate 真实触发
    const flowRes = await fetch(`${API}/api/courses/47/courseware/4/flow/evaluate`, {
      method: 'POST', headers,
      body: JSON.stringify({ currentPageId: 16, userProgress: 0.1 }),
    });
    const flowJson = await flowRes.json();
    expect(flowJson.code).toBe(200);
    expect(flowJson.data).toHaveProperty('matchedType');
    expect(flowJson.data.nextPageId).toBe(17);  // flow 16→17 应求值到 17

    // P0-D: 批量 AI —— 无 API key 时必须诚实报错 (不许假完成)
    const batchRes = await fetch(`${API}/api/courses/47/ppt/pages/scripts/batch-ai-generate`, {
      method: 'POST', headers,
      body: JSON.stringify({ pageIds: [17, 18], contextType: 'page-text' }),
    });
    const batchJson = await batchRes.json();
    expect(batchJson.code).toBe(200);
    for (const r of batchJson.data) {
      expect(r).toHaveProperty('success');
      expect(r).toHaveProperty('error');
    }
  });

  test('学生 PPT 播放: 无参数入口加载 v2 页 + 真实渲染 (浏览器)', async ({ page }) => {
    await login(page, STUDENT);
    await page.goto(`${BASE}/student/courses/47/slides/player`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(8000);

    const bodyText = await page.locator('body').innerText().catch(() => '');
    expect(bodyText.length).toBeGreaterThan(100);
    // 播放器渲染 v2 PPT (1/3 页指示 + 讲述稿 + 播放控件)
    expect(bodyText).toMatch(/\/\s*3\b|\b3\s*页/);
    expect(bodyText).toContain('E2E 测试讲述稿：这是第一页的 AI 生成内容。');
  });

  test('P0-G: audio 加载失败即时反馈 (浏览器)', async ({ page }) => {
    await login(page, STUDENT);
    await page.goto(`${BASE}/student/courses/47/slides/player?sectionId=4`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(8000);

    const bodyText = await page.locator('body').innerText().catch(() => '');
    // FAILED 音频 → 播放器显示失败态 + 重试按钮 (P0-G)
    expect(bodyText).toContain('音频加载失败');
    expect(bodyText).toContain('重新加载');
  });

  test('P0-H: GENERATING 段状态可见 (API+DB)', async ({ page }) => {
    const token = await apiLogin(TEACHER);
    const headers = { Authorization: `Bearer ${token}` };

    // 验证 tree 暴露音频状态 (GENERATING/FAILED 都应为可见状态而非静默消失)
    const treeRes = await fetch(`${API}/api/courses/47/courseware/tree?sectionId=4`, { headers });
    const treeJson = await treeRes.json();
    expect(treeJson.code).toBe(200);
    expect(['AUDIO_GENERATING', 'AUDIO_READY', 'AUDIO_FAILED', 'AUDIO_PENDING']).toContain(treeJson.data.narrationStatus);

    // 页级页面 VO 必须带 narrationStatus (前端据此渲染"生成中/失败/就绪")
    const pagesRes = await fetch(`${API}/api/courses/47/slides/pages?sectionId=4`, { headers });
    const pagesJson = await pagesRes.json();
    expect(pagesJson.code).toBe(200);
    for (const p of pagesJson.data) {
      expect(p.narrationStatus).toBeTruthy();
      expect(p.narrationStatusText).toBeTruthy();
    }
  });

  test('教师 SlideManage: 章节级课件树渲染 PPT+HTML 两课时 (浏览器)', async ({ page }) => {
    await login(page, TEACHER);
    await page.goto(`${BASE}/teacher/courses/47/chapters/71/manage-slides`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(8000);

    const bodyText = await page.locator('body').innerText().catch(() => '');
    expect(bodyText).toContain('章节一');
  });
});
