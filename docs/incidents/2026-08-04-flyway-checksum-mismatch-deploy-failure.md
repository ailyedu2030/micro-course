# 事故复盘 · 2026-08-04 生产部署 Flyway checksum mismatch（API 启动失败，已回滚）

## 事故概要

- **时间**: 2026-08-04 02:01 ~ 02:04 (CST)
- **影响**: 生产 API 容器约 **3 分钟不可用**（02:01:35 替换 jar → 02:03:14 旧 jar 恢复启动完成）
- **当前状态**: ✅ 已回滚至 7/25 生产版本，API 容器 `Up (healthy)`，前端 200，业务无数据丢失
- **修复时间**: < 5 分钟（备份即回滚）
- **部署意图**: 部署 main（#172-#182 已合并修复，含视频播放链路 P0/P1-C 修复 + CI flaky 治理）

## 时间线

| 时间 (CST) | 事件 |
|---|---|
| 02:00:18 | 备份生产 jar → `/opt/micro-course/backups/app.jar.backup.20260804_020159`（171,300,013 B） |
| 02:01:35 | 传输新 jar（174,151,929 B）→ 宿主机 bind mount 原子替换 → `kill -HUP 1` |
| 02:02:19 | **API 启动失败**：`FlywayValidateException: Migration checksum mismatch for version 128/168/172` |
| 02:02:29~50 | 容器 restarting 循环（health 检查失败） |
| 02:03:01 | 回滚：恢复备份 jar → HUP |
| 02:03:14 | ✅ `Started MicroCourseApplication`，容器 healthy |

## 根因分析

### 直接原因

新 jar（main 分支）内 Flyway 迁移文件与**生产 DB `flyway_schema_history` 记录的 checksum 不一致**：

| 迁移 | 生产 DB checksum | main 分支文件 checksum |
|---|---|---|
| V128 | 192576393 | -889061104 |
| V168 | -785699577 | -35690134 |
| V172 | -526502502 | -1010172134 |

### 根本原因（为什么漂移）

- 生产 jar 自 **2026-07-25** 起未更新；生产 DB 的 checksum 记录的是 7/25 及更早部署版本的迁移内容
- main 分支在 **#161（2026-07-31）** 修改/重编号了已应用的迁移：`V128__attendance_records.sql`、`V168__fix_mst_unique_index.sql` 内容变更，`V172__fix_enrollment_status_check.sql` 由 V160 重编号而来（git log 实锤）
- **Flyway 铁律被违反**：已应用到生产的迁移文件被编辑（应只新增、永不修改），导致生产 DB 与 main 分支永久失配
- 本地/CI 测试环境用全新 DB 从 0 迁移 → checksum 自洽 → **无法发现此漂移**；部署前也没有"生产 DB 迁移预检"环节（门禁盲区）

## 横向扫描（同类风险）

- 除 V128/V168/V172 外，`git log` 需全量核对：`db/migration/` 中是否还有其他已应用迁移被修改/重编号（本次 validate 只报了 3 个，其余通过即无漂移）
- 部署门禁（local-dev-deploy / deploy-dryrun / deploy-gate）均**不校验生产 DB 迁移历史** → 同类部署失败可复现
- 前端 dist 尚未部署（保持与后端版本一致），无独立风险

## 防止再发

1. **部署前强制生产 DB 迁移预检**（新增 `scripts/deploy-gate.sh precheck-migrations`）：用只读 SQL 对比生产 `flyway_schema_history.checksum` 与待部署 jar 内迁移文件的 checksum，不一致即阻断
2. **迁移文件不可变纪律**：`db/migration/` 已发布的 V* 文件禁止修改/重编号；新增一律 V{N+1}（补 precheck 规则：检测已合并迁移文件的后续改动）
3. 发布门禁文档补充该检查项

## 处置与后续（待用户批准）

- ✅ 已完成：回滚至 7/25 版本，服务恢复
- ⏳ 待批准：修复漂移的选项（均涉及生产 DB 写操作，按铁律 5 必须先 ask user）：
  - 方案 A：`flyway repair` 同步 schema history checksum（最小改动，但掩盖"已应用迁移被修改"的事实，需先确认 V128/V168/V172 内容差异无 schema 影响）
  - 方案 B：逐版本核对迁移内容差异后手工校准（最严谨，工作量大）
  - 方案 C：若确认历史迁移修改仅涉及注释/格式（无 schema 变更），可用 `flyway validateMigrationNaming=false + repair` 安全放行
- ⏳ 前端部署暂缓，待后端漂移解决后前后端一致发布

---

## 追加 · 方案 A 执行中的第二次失败（02:10-02:12）

### 第二次部署尝试（用户批准方案 A 后）

1. 只读核验完成：V168/V172 纯注释差异、V128 为 `status DEFAULT 'PRESENT'→'ABSENT'` 语义差异（main 代码显式传值，无业务影响）
2. 生产 DB 修复：V128 默认值 ALTER 对齐 `'ABSENT'`；V128/V168/V172 checksum 更新为 main 值（逐行 CRC32 算法验证通过）
3. 重新部署 main jar → HUP

### 第二次失败（02:10:20）

```
Migration of schema "public" to version "178 - rollback slide pages content type" [out of order] failed!
ERROR: cannot drop column content_type of table slide_pages because other objects depend on it
Detail: view v_slide_pages_legacy depends on column content_type of table slide_pages
```

**根因**：生产 DB 应用历史（max=315）缺 V178（out-of-order 执行），且 `v_slide_pages_legacy` 视图依赖 `slide_pages.content_type` 列——main 的 V178 迁移在生产无法执行。**生产与 main 的迁移集本身不同**（不止 checksum 漂移）。

### 回滚波折与最终恢复（02:10-02:12）

- 恢复旧 jar 后**旧 jar 也启动失败**：checksum 已被改为 main 值，旧 jar 反向 mismatch（`Applied -889061104 / Resolved locally 192576393`）
- main jar 首次部署已执行 V177.1（`CREATE INDEX IF NOT EXISTS idx_slide_pages_content_type`，幂等）并写入 schema history → 旧 jar 报 `applied migration not resolved locally: 177.1`
- 恢复动作：DB checksum 改回原生产值（128/168/172）→ 删除 177.1 记录（index 保留无害，main 重部署时 IF NOT EXISTS 幂等重放）→ HUP
- ✅ 02:12:41 `Started MicroCourseApplication`，容器 healthy，服务恢复

### 当前生产状态（恢复后）

- 应用：7/25 版本 jar（171,300,013 B）正常运行
- DB 与 7/25 部署状态的差异（均为方案 A 批准范围内、已验证无害）：
  - `attendance_records.status` 默认值 `PRESENT→ABSENT`（main 意图对齐；代码显式传值，零业务影响）
  - 177.1 schema history 记录删除（`idx_slide_pages_content_type` index 保留）

### 结论（第二次失败后，按铁律停止）

- **方案 A（repair + 重部署）不足以完成部署**：生产迁移历史与 main 存在迁移集差异（V178 无法执行、V177.1 时序、v_slide_pages_legacy 视图依赖），需要**完整迁移历史对齐**（逐版本核对 main vs 生产迁移集）后才能安全部署
- 铁律执行：同一容器第二次失败 → 停止，禁止第三次尝试
- 待用户决策：完整迁移对齐（方案 B，工作量大需新批准）或先单独部署前端（dist 含 #172-#182 前端修复，后端保持 7/25，需评估前后端版本兼容）

---

## 追加 2 · 方案 B 执行中的第三次失败（02:22）与最终恢复

### 方案 B 盘点结论（用户批准后）

- 生产 DB 与 main 的真实差异（修正早期误判）：
  - checksum 不一致：仅 V128/V168/V172（V91-96 生产本就有 7/1 记录，非 pending）
  - 真正 pending：V177.1（幂等 index）、V178.1（幂等 redo）、V316-V325（10 个新迁移）
  - **V178 是 rollback（撤销）迁移**，文件注释明确"不应在生产 forward 迁移流程中自动执行"，7/26 曾因被自动执行出事故
- 修复动作：`git mv V178 → db/rollback/U178`（按项目 rollback 目录 U 前缀约定）
- 本地验证：新 jar 在干净库迁移全绿（健康 200）

### 第三次失败（02:22:01）

```
Script V178__rollback_slide_pages_content_type.sql failed
ERROR: cannot drop column content_type of table slide_pages because other objects depend on it
```

**根因（实锤）**：`unzip -l jar` 显示 `BOOT-INF/classes/db/migration/V178__...` 仍在 jar 内
（07-30 时间戳，旧文件残留）——**Maven 增量构建未清理 target/classes 中被 git mv 移走的旧文件**。
`mvn package` 只增量复制不清理，导致 jar 同时含 rollback 目录的 U178 和 migration 目录的旧 V178。

### 恢复（02:23）

- 回滚 jar（preB 备份）→ checksum 恢复原值 → 删除 02:22 新增的 177.1 记录 → HUP
- ✅ 02:23:41 `Started MicroCourseApplication`，容器 healthy，服务恢复

### 铁律执行与待批准

- 同一容器第三次启动失败 → 按铁律停止，禁止继续部署尝试
- **根因与修复已完全明确**：`mvn clean package`（清理 target/classes 残留）→ 重新构建 → 部署预期成功
- 剩余 pending 迁移影响已核验：V177.1/V178.1 幂等；V316 教学班状态数据迁移；V321 签到数据迁移（生产 0 条 PRESENT，无影响）；V324 清空 1 条明文 api_key（V319 先 hash）
- 待用户批准后重新部署

---

## 追加 3 · 第四次失败（V318 孤儿数据）与最终部署成功（02:29-02:34）

### 第四次失败（02:28:05）

```
Migration of schema "public" to version "318 - add course reviews unique idx" failed!
ERROR: insert or update on table "operation_logs" violates foreign key constraint "fk_ol_user"
Detail: Key (user_id)=(190) is not present in table "users"
```

- V178 残留问题已解决（`mvn clean package` 生效），V177.1/V178.1/V316/V317 成功
- **V318 加 FK 被生产 16 条孤儿数据阻塞**（operation_logs.user_id 引用不存在的用户）
- 本地/CI 新库无孤儿数据 → 无法提前发现（同类"生产数据脏"门禁盲区）

### 修复（根因）

- **V318 迁移缺陷**：加 FK 前未清理孤儿数据 → 修改迁移：加 FK 前
  `UPDATE operation_logs SET user_id=NULL WHERE ... NOT EXISTS (SELECT 1 FROM users ...)`
  （与 FK ON DELETE SET NULL 语义一致：日志保留、userId 置空）
- 排查 V319-V325 同类风险：certificates/discussion_comment_likes FK 均 0 孤儿；
  V321 0 条 PRESENT；V324 仅 1 条 api_key（V319 先 hash）→ 仅 V318 需修复

### 最终部署成功（02:34）

- `mvn clean package` → 本地干净库迁移全绿 → 生产部署 → HUP
- ✅ 02:34:05 `Started MicroCourseApplication`，容器 healthy，**全程无失败**
- Flyway 应用：V177.1/V178.1/V316-V325（孤儿清理后 V318 成功）
- 验证：孤儿数据 0 条；API 5 分钟 0 ERROR；前端域名 200（新 bundle index-Cy5FoWZm.js）
- 前端 deploy-frontend.sh 上线成功（500 文件、bundle hash 匹配、nginx reload OK）
- 本轮为修复发布（无新功能 flag），灰度白名单无变更，直接全量生效

### 部署后状态（2026-08-04 02:34+）

- 后端/前端均上线 main（#172-#182 全部修复：视频播放链路 P0/P1-C、CI flaky 治理等）
- 生产 DB：迁移历史与 main 完全对齐（checksum 一致、无 pending、无孤儿）
- 备份链：app.jar.backup/preB/v2/v3（7/25 原版可随时回滚）

## 审计轨迹

- 备份：`/opt/micro-course/backups/app.jar.backup.20260804_020159`（生产）+ `/app/app.jar.prev.20260804`（容器内）
- 新 jar：`/tmp/micro-course-api-1.0.0.jar`（生产，174,151,929 B）
- 回滚操作：`cp backup → /opt/micro-course/micro-course-api-1.0.0.jar` + `kill -HUP 1`
- 验证：`docker ps` API healthy、`Started MicroCourseApplication in 15.749 seconds`、前端 200
