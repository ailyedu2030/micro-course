# Phase 5 旧表清理倒计时公告 (W37)

> **生效时间**: W37 治理公告
> **目标受众**: 所有教师 / 学员
> **启动日期**: 2026-07-21
> **最早 DROP 日期**: 2026-08-21 (公告满 30 天 + 旧表 0 写入 + DBA 确认)

---

## 公告内容 (前端 Banner)

### 标题
📢 课件架构升级 - 旧版幻灯片管理界面即将下线

### 正文
尊敬的老师/同学:

为了提供更好的课件编辑体验,我们已于 2026-07-21 完成课件架构 v2 升级。

**变更内容**:
- ✅ 新版: 4 面板 PPT/HTML 双类型工作台 + 富文本编辑 + 状态聚合视图
- ⚠️ 旧版 slide_pages 表将在 30 天后下线 (预计 2026-08-21)

**如何切换**:
1. 进入任意课程的"幻灯片管理"页面
2. 顶部右上角找到"新版课件"开关
3. 一键开启即可体验新版 (无需刷新页面)

**数据保障**:
- ✅ 所有课程课件已通过 V310 自动迁移 (138 行)
- ✅ 新旧表数据一致 (CQRS Query Service 实时校验)
- ✅ 旧版 UI 与新版 UI 可同时使用

**反馈渠道**:
- 发现问题请发送 [BUG] + 截图 至 oncall@micro-course.com
- 紧急问题 24 小时内修复

---

## Phase 5 启动条件 (3 项 AND)

```sql
-- 视图查询: 是否达到 DROP 资格
SELECT * FROM v_legacy_cleanup_eligible;
```

| 条件 | 当前 (W37) | 启动要求 | 状态 |
|------|-----------|----------|------|
| legacy_rows = 0 | 1 | 0 | ⏳ 1 行残留 |
| days_since_last_legacy_write >= 30 | — | >= 30 | ⏳ 待启动后计时 |
| DBA 人工确认 | — | yes | ⏳ 待 30 天后 |

---

## 视图监控指标 (Grafana)

| 指标 | Query | 阈值 |
|------|-------|------|
| legacy_rows | `slide_pages_legacy_rows_total` | = 0 (DROP 资格) |
| migrated_rows | `slide_pages_migrated_rows_total` | 持续增长 |
| days_since_last_write | `slide_pages_days_since_legacy_write` | >= 30 |

**告警规则** (W37 新增):
- `legacy_rows > 0` → 提醒 (说明 V310 回填遗漏)
- `days_since_last_legacy_write >= 25` → 提醒 DBA 准备 DROP
- `days_since_last_legacy_write >= 30` → 升级到 P1 (5d 内执行)

---

## DROP 步骤 (Phase 5 收尾)

1. **预检** (DBA 现场):
   ```sql
   SELECT * FROM v_legacy_cleanup_eligible;
   -- 确认 eligible = true
   ```

2. **备份** (审计要求, 保留 90 天):
   ```bash
   pg_dump -t slide_pages micro_course > /backup/slide_pages_$(date +%Y%m%d).sql
   ```

3. **DROP** (一次性, 无 rollback):
   ```sql
   -- 不允许在生产直接执行, 必须通过 Flyway V312 migration
   -- micro-course-api/src/main/resources/db/migration/V312__drop_slide_pages_legacy.sql
   DROP TABLE IF EXISTS slide_pages CASCADE;
   ```

4. **应用层清理**:
   - 删除 `LegacyCoursewareAdapter` (3 个月保留期结束)
   - 删除 `slideService` 相关 endpoint
   - 删除 SlideManage.vue 旧版 UI (新版 100%)

5. **CDN 缓存清理**:
   - `/uploads/slide_pages/*` 资源 30 天后清理

---

## 时间线

| 日期 | 事件 |
|------|------|
| 2026-07-21 | W37 公告启动 (前端 Banner) |
| 2026-07-21 | V311 视图创建 (监控资格) |
| 2026-07-21 | Grafana 告警规则配置 (P3 信息) |
| 2026-08-15 | DBA 预检 (5 天准备) |
| 2026-08-21 | Phase 5 启动 (V312 DROP) |
| 2026-11-21 | CDN 缓存彻底清理 (90 天保留) |

---

## 联系

- 技术负责人: total-engineer@micro-course.com
- DBA: dba@micro-course.com
- 产品: pm@micro-course.com

W37 已落地。