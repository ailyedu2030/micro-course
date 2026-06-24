/**
 * P0 #1: 修改密码后 JWT 立即失效
 *
 * 业务场景: 用户 A 修改密码 → 攻击者持有的旧 token 必须立即失效,
 * 不能等到自然过期 (≤2h)。这是账号接管的最后一道防线。
 *
 * 验证:
 * 1. 登录获得 token
 * 2. 旧 token 访问受保护接口 200
 * 3. 修改密码
 * 4. 旧 token 再次访问 1004 (token已失效)
 *
 * 注意: 测试结束后必须通过 SQL 还原 student 密码为 "123456",否则后续测试
 * 用 student/123456 登录会失败。teardown 在测试末尾执行。
 */
import { test, expect, request as playwrightRequest } from '@playwright/test';
import { execSync } from 'child_process';
import { writeFileSync, unlinkSync } from 'fs';

const API = 'http://localhost:8080';
const USERNAME = 'student';
const PASSWORD_OLD = 'student123';
const PASSWORD_NEW_TEMP = 'abcdef123456';
// 当前 student 密码 "student123" 的 bcrypt 哈希 (10 轮)
// 注意:必须用 SQL 文件 (无 shell 转义) 执行,否则 "$2" "$10" 会被 psql 当作 PREPARE 位置参数
const PASSWORD_OLD_BCRYPT = '$2b$10$ssnz4a4zXYDdDfBM4NvRi.3mngDq4hikZe6SzheP7OzSP5WrA1Pku';

function resetPassword() {
  // 写入临时 SQL 文件并用 psql -f 执行,避免 bash 单/双引号 + $2 $10 位置参数三重 escape 陷阱
  const sql = `UPDATE users SET password = '${PASSWORD_OLD_BCRYPT}' WHERE username = '${USERNAME}';\n`;
  const path = '/tmp/microcourse-reset-pwd.sql';
  writeFileSync(path, sql);
  try {
    execSync(`PGPASSWORD="" psql -h localhost -U postgres -d micro_course -f ${path}`, {
      stdio: 'ignore'
    });
  } catch (e) {
    // ignore — best effort
  } finally {
    try { unlinkSync(path); } catch (_) {}
  }
}

test.describe('P0 #1 - 修改密码后 JWT 失效', () => {
  // 测试前先确保 student 密码是原始值,避免上一个失败运行影响
  test.beforeAll(() => resetPassword());
  test.afterAll(() => resetPassword());

  test('旧 token 在修改密码后必须立即失效', async () => {
    // 1. 登录获得 token
    const ctx = await playwrightRequest.newContext();
    const loginRes = await ctx.post(`${API}/api/auth/login`, {
      data: { username: USERNAME, password: PASSWORD_OLD }
    });
    expect(loginRes.status()).toBe(200);
    const loginBody = await loginRes.json();
    expect(loginBody.code).toBe(200);
    const token = loginBody.data.accessToken;
    expect(token).toBeTruthy();

    // 2. 旧 token 访问 /me 应成功 (基线)
    const me1 = await ctx.get(`${API}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(me1.status()).toBe(200);
    const me1Body = await me1.json();
    expect(me1Body.code).toBe(200);

    // 3. 修改密码
    const changeRes = await ctx.put(`${API}/api/auth/me/password`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { oldPassword: PASSWORD_OLD, newPassword: PASSWORD_NEW_TEMP }
    });
    expect(changeRes.status()).toBe(200);
    const changeBody = await changeRes.json();
    expect(changeBody.code).toBe(200);

    // 4. 旧 token 再次访问 /me 必须被拒绝 (1004: token已失效)
    const me2 = await ctx.get(`${API}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    const me2Body = await me2.json();
    expect(me2Body.code).toBe(1004);
    expect(me2Body.message).toContain('token已失效');

    await ctx.dispose();
  });
});

