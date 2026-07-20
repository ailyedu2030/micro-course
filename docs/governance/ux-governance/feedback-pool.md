# 用户反馈池 · Feedback Pool

> **唯一可信源**：所有用户反馈（应用内/电话/微信/GitHub/邮件/教室）必须汇总到本文件。
> **维护人**：产品值班（轮值）
> **格式**：每条反馈一条记录，使用 [templates/feedback-ticket.md](./templates/feedback-ticket.md)
> **审计**：[02 每周体验复盘会](./02-weekly-review-ritual.md) 与 [06 健康度月报](./06-health-report-template.md) 必看

---

## 当前活跃反馈 (Open Feedback)

> 按状态分组，每条记录包含完整模板。下一节新增反馈追加到顶部。

<!-- FB-NEW:START - 由产品值班追加 -->
(empty)
<!-- FB-NEW:END -->

### 新反馈待定级

(empty)

### 已定级 · 修复中 (in-fix)

(empty)

### 已修复 · 待用户回访 (verified-by-user)

(empty)

---

## 最近 30 天已闭环反馈

> 仅展示 closed 与 wontfix。每条附 issue 链接 + 用户回访记录。

(empty)

---

## 月度统计

| 月份 | 总反馈 | P0-C | P1-C | P1-R | P2-R | P3-R | 已闭环 | SLA 达成率 |
|------|--------|------|------|------|------|------|--------|-----------|
| 2026-07 | 0 | 0 | 0 | 0 | 0 | 0 | — | — |

---

## 跨期高频主题（季度统计）

| 主题 | 次数 | 主要触达点 | 已投入 issue | 状态 |
|------|------|-----------|-------------|------|
| — | — | — | — | — |

---

## 操作约定

1. **新建反馈**：从顶部追加，使用 [templates/feedback-ticket.md](./templates/feedback-ticket.md) 全部字段
2. **状态变更**：直接在原条目中修改 `状态` 字段；不要删除
3. **跨期归档**：超过 90 天的 closed 反馈移到 `[YYYY-MM-closed-archive.md]` 单独文件
4. **回访原则**：closed 状态必须有"用户回访记录"或填写 wontfix 决策
5. **指标映射**：每条反馈的"主指标 / 辅指标"必须落到 [01 量化标准](./01-metrics-standard.md) 的某行

---

> **失效红线**：
> - 任何反馈在 IM 群"听一句"没录入本池 = 流失
> - 缺失字段超过 24h = 产品值班 24h SLA 失效
> - closed 状态无回访 / 无 wontfix 决策 = 不允许关闭
