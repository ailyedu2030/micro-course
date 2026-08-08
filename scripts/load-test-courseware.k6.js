// =============================================================================
// load-test-courseware.k6.js — Phase 14 课件 getPages 压测场景（k6）
//
// 用途: 验证 PPT/HTML 课件 getPages 在真实 DB 构建路径下的性能
//   （Q-2 N+1 修复后: pages 1 SQL + active scripts 1 SQL + audios 1 SQL + flow 1 SQL）。
//
// 关键设计: 每个请求轮转不同 sectionId（SECTION_BASE .. +SECTION_COUNT），
//   使每次请求都是 Redis 缓存未命中 → 强制走 DB 构建路径，真实测量 N+1 修复效果。
//
// 用法（由 load-test-courseware.js 编排调用）:
//   k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<jwt> \
//     -e COURSE_ID=990001 -e SECTION_BASE=9910001 -e SECTION_COUNT=120 \
//     -e VUS=10 -e DURATION=30s -e ENDPOINT=pages \
//     --summary-trend-stats="avg,min,med,max,p(50),p(90),p(95),p(99)" \
//     --summary-export=<out.json> scripts/load-test-courseware.k6.js
//
// ENDPOINT:
//   pages = 学生播放器路径 GET /api/courses/{cid}/slides/pages?sectionId=  (Q-2 已修复)
//   tree  = 教师课件树路径 GET /api/courses/{cid}/courseware/tree?sectionId= (N+1 对比)
// =============================================================================

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const COURSE_ID = __ENV.COURSE_ID || '990001';
const SECTION_BASE = parseInt(__ENV.SECTION_BASE || '9910001', 10);
const SECTION_COUNT = parseInt(__ENV.SECTION_COUNT || '120', 10);
const ENDPOINT = __ENV.ENDPOINT || 'pages'; // pages | tree
const VUS = parseInt(__ENV.VUS || '10', 10);
const DURATION = __ENV.DURATION || '30s';

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    // 设计文档性能预算: p99 < 200ms
    http_req_duration: ['p(99)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const sectionId = SECTION_BASE + ((__VU + __ITER) % SECTION_COUNT);

  let url;
  if (ENDPOINT === 'tree') {
    url = `${BASE_URL}/api/courses/${COURSE_ID}/courseware/tree?sectionId=${sectionId}`;
  } else {
    url = `${BASE_URL}/api/courses/${COURSE_ID}/slides/pages?sectionId=${sectionId}`;
  }

  const res = http.get(url, {
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      Accept: 'application/json',
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has data field': (r) => r.body && r.body.includes('"data"'),
  });
}
