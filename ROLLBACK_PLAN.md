# 微课平台应急回滚预案

> 部署失败或重大故障时执行。优先 5 分钟应用层回滚，如数据库结构变更导致问题则执行 30 分钟回滚。

---

## 5 分钟回滚（应用层）

当应用启动失败、接口大量报 500、或健康检查持续不通过时，执行应用层回滚。

### 步骤 1：停止当前应用

```bash
systemctl stop micro-course-api
```

### 步骤 2：切回上一个稳定版本

```bash
# 假设上一个稳定版本目录为 v1.17.0
ln -sf /opt/micro-course/versions/v1.17.0 /opt/micro-course/current

# 确认切换成功
ls -la /opt/micro-course/current
```

### 步骤 3：启动旧版本

```bash
systemctl start micro-course-api

# 确认启动成功
systemctl status micro-course-api
```

### 步骤 4：验证回滚成功

```bash
# 健康检查
curl -s http://localhost:8080/actuator/health
# 预期: {"status":"UP"}

# 检查日志无新增 ERROR
journalctl -u micro-course-api --since="5 minutes ago" | grep -i error
```

### 版本管理建议

```bash
# 部署前创建版本目录
mkdir -p /opt/micro-course/versions/v1.17.0
mkdir -p /opt/micro-course/versions/v1.18.0  # 新版本

# 部署后如新版本稳定，将新版本标记为当前
ln -sf /opt/micro-course/versions/v1.18.0 /opt/micro-course/current
```

---

## 30 分钟回滚（数据库层）

> ⚠️ **警告**：数据库层回滚风险较高，仅在确认 Flyway 迁移导致数据损坏或无法启动时执行。

### 触发条件

- V80/V81 迁移执行后应用无法启动
- 迁移导致数据损坏（如误删数据、约束冲突）
- 需要立即恢复服务且应用层回滚无效

### 回滚前准备

```bash
# 1. 立即停止应用（防止写入更多数据）
systemctl stop micro-course-api

# 2. 确认有完整备份可恢复
ls -la /backup/micro_course_*.dump
```

### 回滚 V81 添加的 FK 约束

```sql
-- 连接到数据库
psql -U microcourse_user -d micro_course

-- 删除 V81 添加的所有 FK 约束（幂等操作）
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_user_id_fkey;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_course_id_fkey;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_bundle_id_fkey;
ALTER TABLE course_bundles DROP CONSTRAINT IF EXISTS course_bundles_creator_id_fkey;
ALTER TABLE course_bundle_items DROP CONSTRAINT IF EXISTS course_bundle_items_bundle_id_fkey;
ALTER TABLE course_bundle_items DROP CONSTRAINT IF EXISTS course_bundle_items_course_id_fkey;
ALTER TABLE lessons DROP CONSTRAINT IF EXISTS lessons_chapter_id_fkey;
ALTER TABLE lessons DROP CONSTRAINT IF EXISTS lessons_course_id_fkey;
ALTER TABLE course_slides DROP CONSTRAINT IF EXISTS course_slides_lesson_id_fkey;

-- 删除 V81 添加的索引
DROP INDEX IF EXISTS idx_users_student_no;
DROP INDEX IF EXISTS idx_users_email;
DROP INDEX IF EXISTS idx_users_username;
```

### 回滚 V80 添加的 deleted_at 列

```sql
-- 为以下 11 张表删除 deleted_at 列
ALTER TABLE banners DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE classes DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE course_categories DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE course_tag_relations DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE departments DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE majors DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE tags DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE question_tag_relations DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE teaching_classes DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE course_bundles DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE course_bundle_items DROP COLUMN IF EXISTS deleted_at;
```

### 回滚 V79 添加的 status 列

```sql
-- 删除 course_reviews 的 status 列（仅在确认 V79 是问题来源时执行）
ALTER TABLE course_reviews DROP COLUMN IF EXISTS status;
DROP INDEX IF EXISTS idx_course_reviews_status;
```

### 标记 Flyway 回滚（可选）

```sql
-- 将 flyway_schema_history 中的失败记录标记为回滚（不推荐，优先恢复备份）
-- DELETE FROM flyway_schema_history WHERE version IN ('V79','V80','V81');
```

### 恢复数据库备份（最终手段）

```bash
# 如果上述回滚无效，从备份恢复
pg_restore -U microcourse_user -d micro_course -c /backup/micro_course_YYYYMMDD_HHMMSS.dump
```

### 验证数据库回滚

```sql
-- 确认 FK 约束已删除
SELECT constraint_name FROM information_schema.table_constraints
WHERE table_name IN ('orders','course_bundles','course_bundle_items','lessons','course_slides')
AND constraint_type = 'FOREIGN KEY';

-- 确认 deleted_at 列已删除
SELECT column_name FROM information_schema.columns
WHERE table_name = 'banners' AND column_name = 'deleted_at';  -- 应返回空

-- 确认 status 列已删除
SELECT column_name FROM information_schema.columns
WHERE table_name = 'course_reviews' AND column_name = 'status';  -- 应返回空
```

### 重启应用验证

```bash
systemctl start micro-course-api
sleep 10
curl -s http://localhost:8080/actuator/health
```

---

## 快速回滚脚本

```bash
#!/bin/bash
# quick_rollback.sh - 5分钟应用层回滚
set -e

CURRENT_VERSION=${1:-"v1.17.0"}
CURRENT_LINK="/opt/micro-course/current"
VERSIONS_DIR="/opt/micro-course/versions"

echo "=== 开始快速回滚 ==="
echo "目标版本: $CURRENT_VERSION"

systemctl stop micro-course-api
echo "[1/4] 应用已停止"

ln -sf ${VERSIONS_DIR}/${CURRENT_VERSION} ${CURRENT_LINK}
echo "[2/4] 软链接切换到 $CURRENT_VERSION"

systemctl start micro-course-api
echo "[3/4] 应用已启动"

sleep 5
HEALTH=$(curl -s http://localhost:8080/actuator/health)
echo "[4/4] 健康检查: $HEALTH"

if echo "$HEALTH" | grep -q "UP"; then
    echo "=== 回滚成功 ✅ ==="
else
    echo "=== 回滚失败，请检查日志 ==="
    exit 1
fi
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

## 回滚决策树

```
部署/故障发生
    │
    ├─ 应用启动失败/健康检查不通过
    │   └─ 5分钟回滚（应用层）
    │
    ├─ 数据库迁移失败
    │   ├─ Flyway 迁移错误 → 修复 SQL → 重跑迁移
    │   └─ 迁移导致数据问题 → 30分钟回滚（数据库层）
    │
    └─ 严重数据损坏
        └─ 从备份恢复（可能超过 30 分钟）
```

---

## 回滚后通知

回滚完成后需通知：

- [ ] 企业方对接人（告知服务中断时长）
- [ ] 项目经理（记录事件）
- [ ] 后端团队（复盘分析）
