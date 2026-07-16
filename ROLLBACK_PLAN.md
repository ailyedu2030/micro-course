# 微课平台应急回滚预案

> 部署失败或重大故障时执行。优先 5 分钟应用层回滚，如数据库结构变更导致问题则执行 30 分钟回滚。
>
> **最后更新**: 2026-07-09 (v1.21.1 每教师独立 API Key)

---

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.21.1 | 2026-07-09 | 每教师独立 API Key（Hermes webhook 改造） |
| v1.21.0 | 2026-07-09 | Docker 部署适配（Hermes 共享 API Key） |

## 5 分钟回滚（应用层）

当应用启动失败、接口大量报 500、或健康检查持续不通过时，执行应用层回滚。

### 生产环境信息

| 容器 | 宿主机 IP | 用途 |
|------|-----------|------|
| `micro-course-micro-course-api-1` | 100.74.122.13 | 后端 API (8080) |
| `micro-course-micro-course-admin-1` | 100.74.122.13 | 前端 Admin |
| `micro-course-postgres-1` | 100.74.122.13 | PostgreSQL (5432) |
| `micro-course-redis-1` | 100.74.122.13 | Redis (6379) |

### 步骤 1：备份当前问题版本

```bash
# 登录生产服务器
ssh ubuntu@100.74.122.13

# 备份当前运行中的 jar（以防需要回滚到当前版本）
docker cp micro-course-micro-course-api-1:/app/app.jar /tmp/app.jar.backup.$(date +%Y%m%d_%H%M%S)
```

### 步骤 2：回滚到上一个稳定版本

```bash
# 假设上一个稳定版本 jar 在 /tmp/ 下（部署时已备份）
# 例如回滚到 v1.20.2
docker cp /tmp/micro-course-api-1.0.0.jar.v1.20.2 micro-course-micro-course-api-1:/app/app.jar

# 优雅重启（不是 kill -9）
docker exec micro-course-micro-course-api-1 kill -s HUP 1

# 确认重启
sleep 5
docker logs micro-course-micro-course-api-1 --since=30s | grep -E "Started|ERROR|Exception"
```

### 步骤 3：验证回滚成功

```bash
# 健康检查
curl -s http://localhost:8080/actuator/health
# 预期: {"status":"UP"}

# 检查日志无新增 ERROR（最近 5 分钟）
docker logs micro-course-micro-course-api-1 --since=5m | grep -i error | head -20

# 前端也可检查
curl -s http://localhost:8088/ | head -5
```

---

## 30 分钟回滚（数据库层）

> ⚠️ **警告**：数据库层回滚风险较高，仅在确认 Flyway 迁移导致数据损坏或无法启动时执行。

### 触发条件

- Flyway 迁移执行后应用无法启动
- 迁移导致数据损坏（如误删数据、约束冲突）
- 需要立即恢复服务且应用层回滚无效

### 回滚前准备

```bash
# 1. 立即停止应用（防止写入更多数据）
docker exec micro-course-micro-course-api-1 kill -s TERM 1

# 2. 确认有完整备份可恢复
ssh ubuntu@100.74.122.13 "docker exec micro-course-postgres-1 pg_dump -U microcourse_user micro_course" > /tmp/micro_course_backup.$(date +%Y%m%d_%H%M%S).sql
```

### 回滚 V174 (hermes_course_mapping 表)

```sql
-- 连接到生产数据库
psql -h 100.74.122.13 -U microcourse_user -d micro_course

-- 删除 hermes_course_mapping 表（幂等操作）
DROP TABLE IF EXISTS hermmes_course_mapping;

-- 从 flyway_schema_history 中移除记录
DELETE FROM flyway_schema_history WHERE version = 'V174';
```

### 恢复数据库备份（最终手段）

```bash
# 从备份恢复
cat /tmp/micro_course_backup.YYYYMMDD_HHMMSS.sql | docker exec -i micro-course-postgres-1 psql -U microcourse_user -d micro_course
```

### 验证数据库回滚

```sql
-- 确认 hermes_course_mapping 表已删除
SELECT table_name FROM information_schema.tables WHERE table_name = 'hermes_course_mapping';
-- 应返回空

-- 确认 flyway 记录已移除
SELECT version, description FROM flyway_schema_history WHERE version = 'V174';
-- 应返回空
```

### 重启应用验证

```bash
docker exec micro-course-micro-course-api-1 kill -s HUP 1
sleep 10
curl -s http://localhost:8080/actuator/health
```

---

## 版本历史（最近 3 个版本）

| 版本 | 部署时间 | 变更 | 回滚命令 |
|------|----------|------|----------|
| v1.21.2 | 2026-07-17 | P1 Stage 1: courses/chapters/sections 元信息 (V194-196) | db: `psql ... < db/rollback/V196__rollback_add_section_meta.sql` <br> app: `docker cp /tmp/app.jar.backup.v1.21.1 micro-course-micro-course-api-1:/app/app.jar` |
| v1.21.1 | 2026-07-16 | R4 修复: multipart 持久化 + Files.copy + HTML migration (V193) | `docker cp /tmp/app.jar.backup.v1.21.0 micro-course-micro-course-api-1:/app/app.jar` |
| v1.21.0 | 2026-07-09 | Hermes webhook 课程同步 (V174 新表) | `docker cp /tmp/app.jar.backup.v1.21.0 micro-course-micro-course-api-1:/app/app.jar` |

---

## 快速回滚脚本

```bash
#!/bin/bash
# quick_rollback.sh - 5分钟应用层回滚
# 用法: bash quick_rollback.sh <backup_file>
set -e

BACKUP_JAR=${1:-"/tmp/app.jar.backup.v1.20.2"}
CONTAINER="micro-course-micro-course-api-1"

echo "=== 开始快速回滚 ==="
echo "源文件: $BACKUP_JAR"

# 复制并重启
docker cp "$BACKUP_JAR" ${CONTAINER}:/app/app.jar
echo "[1/3] JAR 已复制"

docker exec ${CONTAINER} kill -s HUP 1
echo "[2/3] 应用已重启 (HUP)"

sleep 8
HEALTH=$(curl -s http://localhost:8080/actuator/health || echo "DOWN")
echo "[3/3] 健康检查: $HEALTH"

if echo "$HEALTH" | grep -q "UP"; then
    echo "=== 回滚成功 ✅ ==="
else
    echo "=== 回滚失败，请检查日志 ==="
    docker logs ${CONTAINER} --since=1m | tail -30
    exit 1
fi
```

---

## 灰度回滚

如果灰度发布后发现问题，但还没全量：

```bash
# 从灰度白名单移除问题账号
bash scripts/gray-release.sh remove <user>

# 或者回滚到指定版本
bash scripts/gray-release.sh roll-back <version>
```

---

## 回滚决策树

```
部署/故障发生
    │
    ├─ 应用启动失败/健康检查不通过
    │   └─ 5分钟回滚（应用层）→ docker cp + HUP
    │
    ├─ 数据库迁移失败
    │   ├─ Flyway 迁移错误 → 修复 SQL → 重跑迁移
    │   └─ 迁移导致数据问题 → 30分钟回滚（数据库层）
    │
    └─ 严重数据损坏
        └─ 从备份恢复（可能超过 30 分钟）
```

---

## 紧急联系人

| 角色 | 姓名 | 电话 | 邮箱 | 备注 |
|------|------|------|------|------|
| 后端负责人 | - | - | - | 技术支持第一联系人 |
| 数据库 DBA | - | - | - | 数据库问题定位 |
| 运维工程师 | - | - | - | 服务器、网络问题 |
| 企业方对接人 | - | - | - | 业务影响确认 |
| 项目经理 | - | - | - | 协调沟通 |

---

## 回滚后通知

回滚完成后需通知：

- [ ] 企业方对接人（告知服务中断时长）
- [ ] 项目经理（记录事件）
- [ ] 后端团队（复盘分析）
