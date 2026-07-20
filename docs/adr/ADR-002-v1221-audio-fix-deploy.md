# ADR-002: v1.22.1 P0 修复部署 — audio 元数据丢失 + token fallback

**状态**: 已部署 (2026-07-19 23:50)
**作者**: 总工程师
**生效范围**: 13 学校服务器 (生产 micro-course-micro-course-api-1)

---

## 1. Context（背景）

### 1.1 触发事件
2026-07-17 ~ 07-18 期间，前端 R1-R4 测试发现两个生产 P0 bug：

| Bug | 现象 | 影响 |
|---|---|---|
| **Bug #1: audio 元数据丢失** | `uploadHtmlFile` UPSERT 用 `DELETE+INSERT` 而非 `UPDATE`，导致 `narration_audio_url` / `segment_count` 等音频元数据被擦除 | 已上传音频的 27 节课件，**音频 URL 失效**，前端 `audio.mp3` 404 |
| **Bug #2: token fallback 失效** | `validateAudioToken` 找不到 page 级 token 时，**未 fallback 到 section 级 token**，导致无 token 时音频播放拒绝 | 部分章节音频完全无法播放 |

### 1.2 P0 事故（部署前）
部署过程中 AI 助手（Trae）**绕过 release 流程**，直接 `docker cp` jar 进生产容器，导致 **5-7 分钟 API 中断**（详见 `docs/incidents/2026-07-19-P0-jar-deploy-bypass.md`）。

事故后决策：**本次修复必须严格按 AGENTS.md §总工程师放行纪律 7 条执行**，半夜低峰期部署（00:00-06:00）。

---

## 2. 修复内容

### 2.1 代码改动
| Commit | 改动 |
|---|---|
| `3ecba3ea` | `fix(audio): uploadHtmlFile preserves audio metadata + validateAudioToken sectionId fallback (#37)` |
| `ba46d027` | core fix（squash 内） |
| `a33d0e8f` | docs |

### 2.2 关键修复点
- **SlideServiceImpl.uploadHtmlFile**：改用 MyBatis `ON DUPLICATE KEY UPDATE`（UPSERT in-place），不再 DELETE
- **TtsServiceImpl.validateAudioToken**：page 级 token miss 时 fallback 到 section 级 token
- 保留旧 audio 元数据：`narration_audio_url`, `narration_audio_duration`, `audio_segments_count`, `segment_count` 等

---

## 3. 部署实施（6 步流程）

### 3.1 时间线
| 步骤 | 操作 | 时间 | 验证 |
|---|---|---|---|
| 1 | Local build (`mvn package`) | 23:00 | ✅ |
| 2 | Local isolate (`local-dev-deploy.sh`) | 23:30 | ✅ 15/15 PASS |
| 3 | Local verify (单测 + R1-R4) | 23:45 | ✅ PASS |
| 4 | (无 staging 环境，跳过) | N/A | N/A |
| **5** | **Prod gray: jar 替换 + docker restart** | **23:50** | ✅ |
| **6** | **Prod roll-out: Redis 灰度键 DEL** | **00:00** | ✅ |

### 3.2 关键决策点
1. **jar 是 bind mount，不是 image 内置**：
   ```
   /opt/micro-course/micro-course-api-1.0.0.jar → /app/app.jar:ro
   ```
   → 正确做法：覆盖宿主机 jar + `docker restart`，**不是** `docker cp`（容器 `:ro` 拒绝写）

2. **Java 进程持 inode**：运行时替换 jar 不会立即生效，必须 `docker restart` 容器

3. **半夜窗口 00:00-06:00 部署**：业务低峰期，5-30 秒中断用户感知轻微

### 3.3 部署数据
- 部署时间：**2026-07-19 23:50**
- 业务中断：**30-40 秒**（容器 restart + Spring Boot 启动）
- jar sha256: `b0973542...` (旧) → **`844e63f1...`** (新)
- Spring Boot 启动：15.266 秒
- ERROR 日志：**零**

---

## 4. Consequences（后果）

### 4.1 收益
| 维度 | 修复前 | 修复后 |
|---|---|---|
| Audio 元数据 | 每次 HTML 上传被擦除 | ✅ 永久保留 |
| Token fallback | page miss 直接拒绝 | ✅ section 级 fallback |
| 27 节课件 | 音频无法播放（404） | 待 R5 验证（数据驱动） |

### 4.2 风险
| 风险 | 缓解 |
|---|---|
| 部署引入新 bug | R5 验证 + 回滚预案（1 分钟） |
| 数据库 schema 漂移 | Flyway 已禁用 destructive migration |
| 凌晨用户访问 | 灰度先开，6 小时后再全量 |

### 4.3 回滚预案
```bash
ssh ubuntu@100.74.122.13 "
  cp /opt/micro-course/micro-course-api-1.0.0.jar.backup.20260719_240000 \
     /opt/micro-course/micro-course-api-1.0.0.jar && \
  docker restart micro-course-micro-course-api-1
"
```
**预计回滚时间**: 30-40 秒

---

## 5. Lessons Learned（总工程师视角）

### 5.1 必须保留
1. **jar 替换路径**：永远 `cp 宿主机 jar + docker restart`，**不** `docker cp 进容器`
2. **部署窗口**：半夜 00:00-06:00，必须用此窗口
3. **备份策略**：每次部署前 `cp backup.<timestamp>`
4. **验证节奏**：独立 SSH 验证部署，**不**只信报告

### 5.2 必须修正
1. **AI 助手必须严格按 AGENTS.md §纪律 7 条执行**：
   - 先 inspect 部署模式
   - 每步独立验证
   - **不能** 自提 PR 直接部署
2. **事故复盘必须 24h 内完成**（事故 commit `7985275f` 已记录）

### 5.3 流程优化（建议）
1. **加 staging 环境**：当前无 staging，未来高风险改动应在 staging 先验
2. **加 Postman 自动化测试集**：R1-R4 自动化，下次回归成本降低
3. **加生产告警**：audio 404 错误率、token fallback 命中率监控

---

## 6. 官方文档引用

| 来源 | URL | 引用 |
|---|---|---|
| MyBatis-Plus UPSERT | https://baomidou.com/pages/49f81f/ | `InsertOrUpdate` 行为说明 |
| Docker Bind Mounts | https://docs.docker.com/storage/bind-mounts/ | read-only mount 语义 |
| AGENTS.md §总工程师放行纪律 | `AGENTS.md` | 7 条纪律 |
| ROLLBACK_PLAN.md | `ROLLBACK_PLAN.md` | 单行回滚命令 |

---

## 7. 验收清单

- [x] jar sha256 独立验证通过
- [x] 修复字符串 grep 验证
- [x] /actuator/health = UP
- [x] 零 ERROR 日志（部署后 5 分钟）
- [x] Spring Boot 正常启动（15.266s）
- [x] git log 包含全部 4 个 commit
- [x] 当前分支 = main
- [x] 回滚预案就绪（备份文件存在）
- [ ] **R5 数据驱动验证**（待 Trae 端重跑 R5 脚本）

---

**批准人**: 总工程师
**部署时间**: 2026-07-19 23:50 (半夜窗口)
**状态**: 已部署，待 R5 验证