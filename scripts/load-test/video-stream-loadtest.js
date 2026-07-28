/**
 * 压测脚本 — 视频流媒体播放
 *
 * 模拟 200 并发用户播放课程视频
 * 带宽模拟：每个 VU 请求不同的视频片段
 * 阈值：p95 流媒体 TTFB < 200ms
 *
 * 运行:
 *   k6 run -e BASE_URL=http://localhost:8089 -e VIDEO_ID=456 scripts/load-test/video-stream-loadtest.js
 *
 * 前置条件：
 *   需先上传一个视频并获取 VIDEO_ID
 *   用户需已选课（或视频端点公开）
 *   可通过 STUDENT_TOKEN 传入 JWT
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 100 },  // ramp up
    { duration: '3m', target: 200 },  // steady
    { duration: '1m', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.02'],
    // 对流媒体首包时间单独监控
    'http_req_waiting': ['p(95)<200'],
  },
};

export default function () {
  const videoId = __ENV.VIDEO_ID;
  if (!videoId) {
    console.error('VIDEO_ID environment variable is required');
    return;
  }

  const token = __ENV.STUDENT_TOKEN || '';

  const params = {
    headers: {
      'Accept': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  };

  // Step 1: 获取播放信息（HLS m3u8）
  const playRes = http.get(
    `${__ENV.BASE_URL}/api/videos/${videoId}/play`,
    params
  );

  check(playRes, {
    'play endpoint status is 200': (r) => r.status === 200,
    'play response time < 500ms': (r) => r.timings.duration < 500,
    'first byte time < 200ms': (r) => r.timings.waiting < 200,
  });

  // Step 2: 模拟读取 TS 片段
  // HLS 通常将视频切成 10s 片段，这里模拟请求几个片段
  const segmentCount = 3;
  for (let i = 0; i < segmentCount; i++) {
    const segmentIdx = ((__VU * 10 + __ITER * 3 + i) % 60) + 1; // 模拟 60 个片段
    const segmentUrl = `${__ENV.BASE_URL}/api/videos/${videoId}/segments/${segmentIdx}.ts`;

    // 模拟带宽：流媒体片段请求只检查首包时间，不完整下载
    const segRes = http.get(segmentUrl, {
      ...params,
      // 只下载前 64KB 模拟首包
      responseType: 'text',
    });

    check(segRes, {
      'segment status is 200 or 206': (r) => r.status === 200 || r.status === 206,
      'segment waiting time < 200ms': (r) => r.timings.waiting < 200,
    });

    // 片段间短间隔（模拟播放器缓冲）
    sleep(0.1 + Math.random() * 0.2);
  }

  sleep(1 + Math.random() * 0.5); // 1-1.5s 思考时间
}
