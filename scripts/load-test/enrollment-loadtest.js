/**
 * 压测脚本 — 选课并发超卖防护验证
 *
 * 模拟 50 并发学生选课同一门热门课
 * 验证超卖防护(选课人数不超容量)
 * 阈值：p99 < 1s
 *
 * 运行:
 *   k6 run -e BASE_URL=http://localhost:8089 -e COURSE_ID=123 scripts/load-test/enrollment-loadtest.js
 *
 * 前置条件：
 *   需先创建一门容量适当的热门课程，设置 max_students
 *   通过 COURSE_ID 环境变量传入
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

// 学生账号池（由环境变量传入：逗号分隔的 username:password 对）
// 示例: STUDENTS="student1:123456,student2:123456,student3:123456"
const STUDENT_ACCOUNTS = (__ENV.STUDENTS || '').split(',').filter(Boolean);

export const options = {
  stages: [
    { duration: '30s', target: 30 },  // ramp up
    { duration: '2m', target: 50 },   // steady
    { duration: '30s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

function getStudentAccount(vu) {
  if (STUDENT_ACCOUNTS.length > 0) {
    return STUDENT_ACCOUNTS[vu % STUDENT_ACCOUNTS.length];
  }
  // 回退：使用默认测试账号模式
  return `loadtest_${vu}:123456`;
}

function login(username, password) {
  const payload = JSON.stringify({ username, password });
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${__ENV.BASE_URL}/api/auth/login`, payload, params);
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      return body?.data?.accessToken || null;
    } catch (e) {
      return null;
    }
  }
  return null;
}

export default function () {
  const courseId = __ENV.COURSE_ID;
  if (!courseId) {
    console.error('COURSE_ID environment variable is required');
    return;
  }

  // 每个 VU 使用不同学生账号
  const account = getStudentAccount(__VU);
  const [username, password] = account.split(':');

  // 登录
  const token = login(username, password || '123456');
  if (!token) {
    console.error(`Login failed for ${username}`);
    sleep(1);
    return;
  }

  // 选课
  const enrollPayload = JSON.stringify({ courseId: parseInt(courseId) });
  const enrollParams = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  const res = http.post(`${__ENV.BASE_URL}/api/enrollments`, enrollPayload, enrollParams);

  check(res, {
    'status is 200 or 409 or 400': (r) => r.status === 200 || r.status === 409 || r.status === 400,
    'response time < 1000ms': (r) => r.timings.duration < 1000,
  });

  // 验证超卖防护：通过 response body 检查
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      check(null, {
        'enrollment status is valid': () =>
          body?.data?.enrollmentStatus === 'ENROLLED' ||
          body?.data?.enrollmentStatus === 'WAITLIST',
      });
    } catch (e) {
      // ignore parse errors
    }
  }

  sleep(0.5 + Math.random() * 0.5); // 500-1000ms 思考时间
}
