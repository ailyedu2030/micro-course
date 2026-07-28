# 负载压测脚本

使用 [k6](https://k6.io) 编写的核心 API 压测脚本，覆盖微课平台 5 个关键场景。

## 安装

```bash
brew install k6
```

## 快速运行

```bash
# 课程列表（100 并发，5 分钟）
k6 run -e BASE_URL=http://localhost:8089 scripts/load-test/course-list-loadtest.js

# 选课超卖防护（50 并发，3 分钟）
k6 run -e BASE_URL=http://localhost:8089 \
  -e COURSE_ID=123 \
  -e STUDENTS="student1:123456,student2:123456" \
  scripts/load-test/enrollment-loadtest.js

# 结算支付流程（30 并发，3 分钟）
k6 run -e BASE_URL=http://localhost:8089 \
  -e COURSE_ID=123 \
  -e STUDENT_TOKEN=<jwt-token> \
  scripts/load-test/checkout-loadtest.js

# 视频流播放（200 并发，5 分钟）
k6 run -e BASE_URL=http://localhost:8089 \
  -e VIDEO_ID=456 \
  -e STUDENT_TOKEN=<jwt-token> \
  scripts/load-test/video-stream-loadtest.js

# 登录认证（100 并发，5 分钟）
k6 run -e BASE_URL=http://localhost:8089 scripts/load-test/login-loadtest.js
```

## 脚本说明

| 脚本 | 模拟场景 | 并发数 | 持续时间 | 关键阈值 |
|------|----------|--------|----------|----------|
| `course-list-loadtest.js` | 课程广场浏览 | 100 | 5min | p95<500ms, p99<1s |
| `enrollment-loadtest.js` | 选课超卖验证 | 50 | 3min | p99<1s |
| `checkout-loadtest.js` | 结算+支付 | 30 | 3min | p99<2s |
| `video-stream-loadtest.js` | 视频流播放 | 200 | 5min | p95 TTFB<200ms |
| `login-loadtest.js` | 登录认证 | 100 | 5min | p95<300ms |

## 环境变量

| 变量 | 用途 | 示例 |
|------|------|------|
| `BASE_URL` | API 基础 URL | `http://localhost:8089` |
| `COURSE_ID` | 课程 ID（选课/结算脚本） | `123` |
| `VIDEO_ID` | 视频 ID（流媒体脚本） | `456` |
| `STUDENTS` | 学生账号列表（逗号分隔） | `student1:123456,student2:123456` |
| `STUDENT_TOKEN` | JWT Token | `eyJhbGciOi...` |
| `ACCOUNTS` | 登录账号列表（逗号分隔） | `user1:pass1,user2:pass2` |

## 输出解读

k6 运行结束后输出关键指标：

```
http_req_duration..........: avg=123.45ms  p(95)=250.00ms  p(99)=450.00ms
http_req_failed............: 0.00%
✓ { status is 200 }.......: 100.00%
```

- **avg**: 平均响应时间
- **p(95)**: 95% 请求在阈值内（核心指标）
- **p(99)**: 99% 请求在阈值内
- **http_req_failed**: 失败率

## 结果导出

```bash
# JSON 格式输出
k6 run --summary-export=results.json scripts/load-test/course-list-loadtest.js

# 实时输出到 CSV
k6 run --out csv=results.csv scripts/load-test/course-list-loadtest.js
```

## 集成 CI

压测作为 nightly cron 运行，不在 PR 流程中触发。

```yaml
# .github/workflows/nightly-loadtest.yml（示例）
name: Nightly Load Test
on:
  schedule:
    - cron: '0 2 * * *'  # 每天 UTC 2:00
jobs:
  loadtest:
    runs-on: ubuntu-latest
    steps:
      - uses: grafana/k6-action@v0.3.1
        with:
          filename: scripts/load-test/course-list-loadtest.js
          flags: -e BASE_URL=${{ secrets.STAGING_URL }}
```

## 注意事项

1. **预热**: 压测前确保 target API 已运行 30s+，避免冷启动干扰
2. **测试数据**: 选课/结算脚本需要预先准备课程和账号
3. **资源监控**: 压测时配合 `watch docker stats` 观察容器资源
4. **生产安全**: 禁止对生产环境直接压测，一律使用 staging
