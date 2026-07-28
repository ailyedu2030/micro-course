/**
 * 压测脚本 — 登录认证
 *
 * 模拟 100 并发登录（BCrypt 慢哈希）
 * 阈值：p95 < 300ms
 *
 * 运行:
 *   k6 run -e BASE_URL=http://localhost:8089 scripts/load-test/login-loadtest.js
 *
 * 账号池：通过 ACCOUNTS 环境变量传入（逗号分隔 username:password）
 * 默认使用 loadtest_1～100 账号
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

// 账号配置
const ACCOUNTS = (__ENV.ACCOUNTS || '').split(',').filter(Boolean);

export const options = {
  stages: [
    { duration: '1m', target: 50 },   // ramp up
    { duration: '3m', target: 100 },  // steady
    { duration: '1m', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  let username, password;

  if (ACCOUNTS.length > 0) {
    const account = ACCOUNTS[__VU % ACCOUNTS.length];
    [username, password] = account.split(':');
  } else {
    // 默认使用 loadtest 账号
    username = `loadtest_${__VU}`;
    password = '123456';
  }

  const payload = JSON.stringify({ username, password });
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${__ENV.BASE_URL}/api/auth/login`, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response has accessToken': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body?.data?.accessToken !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 300ms': (r) => r.timings.duration < 300,
  });

  sleep(0.5 + Math.random() * 0.5); // 500-1000ms 思考时间
}
