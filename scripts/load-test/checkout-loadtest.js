/**
 * 压测脚本 — 结算 + 支付流程
 *
 * 模拟 30 并发用户结算购买课程 + 支付
 * 阈值：p99 < 2s
 *
 * 运行:
 *   k6 run -e BASE_URL=http://localhost:8089 -e COURSE_ID=123 -e STUDENT_TOKEN=xxx scripts/load-test/checkout-loadtest.js
 *
 * 前置条件：
 *   需准备好学生用户的 JWT token（通过 STUDENT_TOKEN 传入）
 *   或通过 STUDENTS 传入账号列表自动登录
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const STUDENT_ACCOUNTS = (__ENV.STUDENTS || '').split(',').filter(Boolean);

export const options = {
  stages: [
    { duration: '30s', target: 15 },  // ramp up
    { duration: '2m', target: 30 },   // steady
    { duration: '30s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<2000'],
    http_req_failed: ['rate<0.02'],
  },
};

function getStudentAccount(vu) {
  if (STUDENT_ACCOUNTS.length > 0) {
    return STUDENT_ACCOUNTS[vu % STUDENT_ACCOUNTS.length];
  }
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

  // 获取 token
  let token = __ENV.STUDENT_TOKEN;
  if (!token) {
    const account = getStudentAccount(__VU);
    const [username, password] = account.split(':');
    token = login(username, password || '123456');
  }
  if (!token) {
    console.error('No valid token available');
    sleep(1);
    return;
  }

  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  // Step 1: 创建订单
  const orderPayload = JSON.stringify({
    courseId: parseInt(courseId),
    payMethod: 'ALIPAY',
  });

  const orderRes = http.post(
    `${__ENV.BASE_URL}/api/orders`,
    orderPayload,
    authHeaders
  );

  check(orderRes, {
    'order status is 200': (r) => r.status === 200,
    'order response time < 1000ms': (r) => r.timings.duration < 1000,
  });

  let orderId = null;
  if (orderRes.status === 200) {
    try {
      const body = JSON.parse(orderRes.body);
      orderId = body?.data?.id || body?.data?.orderId;
    } catch (e) {
      // ignore
    }
  }

  // 订单创建后短暂停
  sleep(0.2 + Math.random() * 0.3);

  // Step 2: 支付（如果订单创建成功）
  if (orderId) {
    const paymentPayload = JSON.stringify({
      orderId: orderId,
      payMethod: 'ALIPAY',
    });

    const payRes = http.post(
      `${__ENV.BASE_URL}/api/payments`,
      paymentPayload,
      authHeaders
    );

    check(payRes, {
      'payment status is 200': (r) => r.status === 200,
      'payment response time < 2000ms': (r) => r.timings.duration < 2000,
    });
  }

  sleep(1 + Math.random() * 1); // 1-2s 思考时间
}
