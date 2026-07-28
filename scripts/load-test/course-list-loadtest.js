/**
 * 压测脚本 — 课程列表查询
 *
 * 模拟 100 并发用户浏览课程广场
 * 阈值：p95 < 500ms, p99 < 1s
 *
 * 运行:
 *   k6 run -e BASE_URL=http://localhost:8089 scripts/load-test/course-list-loadtest.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },   // ramp up
    { duration: '3m', target: 100 },  // steady
    { duration: '1m', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const params = {
    headers: { 'Accept': 'application/json' },
  };

  // 模拟分页浏览：不同 page 分散热点
  const page = (__VU % 5) + 1;
  const res = http.get(
    `${__ENV.BASE_URL}/api/courses?page=${page}&size=20&sort=createdAt,desc`,
    params
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response body has data': (r) => r.body && r.body.includes('data'),
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(0.3 + Math.random() * 0.4); // 300-700ms 思考时间
}
