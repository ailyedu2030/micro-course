#!/usr/bin/env node
// =============================================================================
// load-test-courseware.js — Phase 14 课件 getPages 压测编排器（Node 26+ 原生 fetch）
//
// 流程:
//   1. 登录获取 admin JWT
//   2. 将 load-test-courseware-seed.sql 导入目标 DB（幂等，生成 30/100 页 PPT + 50 段 HTML 真实数据）
//   3. 清理 Redis 课件页缓存（强制每次请求走 DB 构建路径）
//   4. 对每个场景调用 k6（--summary-export JSON），解析 p50/p95/p99
//   5. 输出报告表 + 对比设计预算 p99 < 200ms
//
// 用法:
//   node scripts/load-test-courseware.js [--api http://localhost:8080]
//        [--pg-host localhost] [--pg-port 5432] [--pg-db micro_course]
//        [--pg-user postgres] [--pg-pass postgres] [--redis-port 6379]
//        [--duration 30s] [--seed] [--no-seed] [--no-bust-cache] [--endpoints pages,tree]
//
// 环境变量兜底: API_BASE / PG_HOST / PG_PORT / PG_DB / PG_USER / PG_PASS / REDIS_PORT
// 生产安全: 默认指向本地 dev DB；禁止对生产 host 使用。
// =============================================================================

const { execFileSync, execSync } = require('node:child_process');
const { existsSync } = require('node:fs');
const path = require('node:path');

// ---- CLI / env 解析 ----
const args = process.argv.slice(2);
function arg(name, envName, fallback) {
  const i = args.indexOf(name);
  if (i !== -1) return args[i + 1];
  if (process.env[envName]) return process.env[envName];
  return fallback;
}
function flag(name) { return args.includes(name); }

const API = (arg('--api', 'API_BASE', 'http://localhost:8080')).replace(/\/$/, '');
const PG_HOST = arg('--pg-host', 'PG_HOST', 'localhost');
const PG_PORT = arg('--pg-port', 'PG_PORT', '5432');
const PG_DB = arg('--pg-db', 'PG_DB', 'micro_course');
const PG_USER = arg('--pg-user', 'PG_USER', 'postgres');
const PG_PASS = arg('--pg-pass', 'PG_PASS', 'postgres');
const REDIS_PORT = arg('--redis-port', 'REDIS_PORT', '6379');
const DURATION = arg('--duration', 'LOAD_DURATION', '30s');
const DO_SEED = flag('--no-seed') ? false : (flag('--seed') ? true : true);
const DO_BUST = flag('--no-bust-cache') ? false : true;
const ENDPOINTS = (arg('--endpoints', 'LOAD_ENDPOINTS', 'pages,tree') || 'pages,tree').split(',');

const ROOT = path.resolve(__dirname, '..');
const SEED_SQL = path.join(__dirname, 'load-test-courseware-seed.sql');
const K6_SCRIPT = path.join(__dirname, 'load-test-courseware.k6.js');
const SUMMARY = '/tmp/mc-loadtest-summary.json';

// 压测课程
const COURSE_PPT = '990001';
const COURSE_HTML = '990002';

// 场景定义: [name, courseId, sectionBase, sectionCount, vus, endpoint]
const SCENARIOS = [
  { name: 'PPT30@10',   courseId: COURSE_PPT, sectionBase: 9910001, sectionCount: 120, vus: 10,  endpoint: 'pages' },
  { name: 'PPT30@50',   courseId: COURSE_PPT, sectionBase: 9910001, sectionCount: 120, vus: 50,  endpoint: 'pages' },
  { name: 'PPT30@100',  courseId: COURSE_PPT, sectionBase: 9910001, sectionCount: 120, vus: 100, endpoint: 'pages' },
  { name: 'PPT100@10',  courseId: COURSE_PPT, sectionBase: 9910201, sectionCount: 20,  vus: 10,  endpoint: 'pages' },
  { name: 'HTML50@10',  courseId: COURSE_HTML, sectionBase: 9930001, sectionCount: 20,  vus: 10,  endpoint: 'pages' },
  { name: 'PPT30@10-tree', courseId: COURSE_PPT, sectionBase: 9910001, sectionCount: 120, vus: 10, endpoint: 'tree' },
];

const COLORS = { ok: '\x1b[32m', fail: '\x1b[31m', dim: '\x1b[90m', reset: '\x1b[0m' };
function out(s = '') { console.log(s); }

// ---- 1. 登录 ----
async function login() {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'password123' }),
  });
  const body = await res.json();
  if (body.code !== 200 || !body.data?.accessToken) {
    throw new Error(`登录失败: ${JSON.stringify(body)}`);
  }
  return body.data.accessToken;
}

// ---- 2. 种子导入 ----
function seed() {
  if (!existsSync(SEED_SQL)) throw new Error(`找不到种子 SQL: ${SEED_SQL}`);
  out(`\n[Seed] 导入 ${path.basename(SEED_SQL)} → ${PG_HOST}:${PG_PORT}/${PG_DB}`);
  const cmd = `psql -h ${PG_HOST} -p ${PG_PORT} -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1 -f "${SEED_SQL}"`;
  const r = execSync(cmd, { env: { ...process.env, PGPASSWORD: PG_PASS }, encoding: 'utf8' });
  const tail = r.split('\n').filter(l => l.includes('|') || l.includes('==')).slice(-12).join('\n');
  out(`[Seed] 完成（见下方统计）:\n${tail}`);
}

// ---- 3. 清理缓存 ----
function bustCache() {
  out(`\n[Cache] 清理 Redis pages 缓存 (port ${REDIS_PORT}) ...`);
  const keys = execSync(
    `redis-cli -p ${REDIS_PORT} --scan --pattern 'mc:courseware:pages:*'`,
    { encoding: 'utf8' }).split('\n').map(s => s.trim()).filter(Boolean);
  if (keys.length) {
    execSync(`printf '%s\\n' ${keys.map(k => `'${k}'`).join(' ')} | xargs redis-cli -p ${REDIS_PORT} del >/dev/null`);
    out(`[Cache] 删除 ${keys.length} 个 key`);
  } else {
    out('[Cache] 无缓存 key');
  }
}

// ---- 4. k6 单场景 ----
async function runK6(sc) {
  const env = {
    ...process.env,
    BASE_URL: API,
    TOKEN: global.TOKEN,
    COURSE_ID: sc.courseId,
    SECTION_BASE: String(sc.sectionBase),
    SECTION_COUNT: String(sc.sectionCount),
    VUS: String(sc.vus),
    DURATION,
    ENDPOINT: sc.endpoint,
  };
  const cmd = [
    'k6', 'run', '--quiet',
    '--summary-trend-stats=avg,min,med,max,p(50),p(90),p(95),p(99)',
    `--summary-export=${SUMMARY}`,
    K6_SCRIPT,
  ];
  try {
    execFileSync(cmd[0], cmd.slice(1), { env, stdio: ['ignore', 'inherit', 'inherit'], encoding: 'utf8' });
  } catch (e) {
    // k6 阈值失败会返回非 0 —— 不代表脚本执行失败，结果仍在 summary 中
    out(`  [warn] k6 返回码 ${e.status}（可能因阈值未达标，结果仍记录）`);
  }
  const { readFileSync } = require('node:fs');
  const sum = JSON.parse(readFileSync(SUMMARY, 'utf8'));
  const d = sum.metrics?.http_req_duration || {};
  const failed = sum.metrics?.http_req_failed || {};
  return {
    p50: d['p(50)'], p95: d['p(95)'], p99: d['p(99)'],
    avg: d.avg, med: d.med, max: d.max,
    rate: typeof failed.value === 'number' ? failed.value : (failed.rate ?? 0),
    iter: sum.metrics?.iterations?.count,
  };
}

// ---- 5. 报告 ----
function report(rows) {
  out('\n==============================================================');
  out('Section A: Load test 结果');
  out('==============================================================');
  out('| 场景 | 并发 | 端点 | p50 | p95 | p99 | 失败率 | 结论 |');
  out('|------|------|------|-----|-----|-----|--------|------|');
  for (const r of rows) {
    const pass = r.p99 < 200;
    const c = pass ? COLORS.ok : COLORS.fail;
    out(`| ${r.name} | ${r.vus} | ${r.endpoint} | ${r.p50?.toFixed(2)}ms | ${r.p95?.toFixed(2)}ms | ${c}${r.p99?.toFixed(2)}ms${COLORS.reset} | ${(r.rate * 100).toFixed(2)}% | ${pass ? 'PASS' : 'FAIL'} |`);
  }
  out('\n预算: 设计文档 p99 < 200ms（15 页 + 15 音频 + flow）');
  const failed = rows.filter(r => r.p99 >= 200);
  out(failed.length
    ? `${COLORS.fail}❌ ${failed.length}/${rows.length} 场景 p99 ≥ 200ms，未达标: ${failed.map(r => r.name).join(', ')}${COLORS.reset}`
    : `${COLORS.ok}✅ 全部 ${rows.length} 个场景 p99 < 200ms，达标${COLORS.reset}`);
}

// ---- main ----
(async () => {
  try {
    out(`Phase 14 Load Test · 目标 ${API} · DB ${PG_HOST}:${PG_PORT}/${PG_DB}`);
    global.TOKEN = await login();
    out(`[Auth] admin token 获取成功 (len=${global.TOKEN.length})`);

    if (DO_SEED) seed();
    if (DO_BUST) bustCache();

    const rows = [];
    for (const sc of SCENARIOS) {
      if (!ENDPOINTS.includes(sc.endpoint)) continue;
      out(`\n[Run] ${sc.name}  (VUS=${sc.vus}, ${DURATION}, ${sc.endpoint})`);
      const r = await runK6(sc);
      rows.push({ ...sc, ...r });
    }
    report(rows);
    out('\n==============================================================');
    out('Section B: 见 scripts/staging-validation.sh 输出（任务 2）');
    out('==============================================================');
  } catch (e) {
    console.error(COLORS.fail + '[FATAL] ' + e.message + COLORS.reset);
    process.exit(1);
  }
})();
