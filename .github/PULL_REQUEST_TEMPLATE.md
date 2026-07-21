# 🚨 UX 治理门禁 (基于 L0 · UX 至上铁律 · 2026-07-21 启动)

> **本模板为强制性**: GitHub 自动以本文件作为 PR 创建表单. 不勾完 / 不补全, 不能进入 review 阶段.
> **依据**: `docs/governance/ux-governance/EXECUTIVE-PRINCIPLE.md` § 一.2 全流程 UX 评审节点

---

## 一、必填项 (缺失任意一项, review 阶段拒收)

### 1.1 UX 评审 Owner (必填)

- [ ] **UX 评审 owner**: @<名字>
- (强制) 当前 PR 涉及前台代码 / 用户感知行为 / 配置项变更 → 必须有 UX 值班或更高 reviewer
- (强制) 涉及无障碍 (a11y) → 必须勾选 § 1.2 checklist

### 1.2 a11y 验收清单 (必填, 完整模板见 [a11y-acceptance-checklist.md](docs/governance/ux-governance/templates/a11y-acceptance-checklist.md))

- [ ] axe-core 0 violations (由 PR bot 自动报)
- [ ] Playwright a11y job 通过
- [ ] 触摸目标 ≥44×44px
- [ ] 键盘 Tab 顺序: 侧栏 → 内容 → 操作 → 提示
- [ ] 焦点环可见 (`:focus-visible`)
- [ ] 文本对比度 ≥4.5:1 (默认主题) / ≥7:1 (老年模式 AAA)
- [ ] 表单有 `<label for>` 或 `aria-label`
- [ ] 模态/抽屉: Esc 关闭 + 焦点回收
- [ ] 老年字号切换到 ≥18px: 样式不破
- [ ] 客服电话按钮在所有新页面底部固定可见
- [ ] 屏幕阅读器走读 (NVDA 或 VoiceOver) — 修改涉及交互必做
- [ ] 拖拽有键盘替代 (方向键 + 空格确认)

### 1.3 端到端体验影响说明 (必填)

- 本次 PR 涉及的用户场景: <一句话>
- 关键路径变化 (前/后): <before> → <after>
- 是否引入体验降级: □ 是 (必须由 UX owner 在 description 复议签字) / □ 否
- 如有, 降级补偿计划: <说明>

---

## 二、变更分类 (单选)

- [ ] 后端 / 数据库
- [ ] 后台管理端 UI
- [ ] 用户端 UI
- [ ] a11y / 老年 / 残障
- [ ] 性能 / 安全 / 监控
- [ ] 文档 / 治理规范 / 治理脚本

---

## 三、跨职能评审签字 (按需)

| 角色 | 是否需 | 签字 |
|------|--------|------|
| **产品值班** | 涉及 UI / 用户感知 | ☐ @product |
| **UX 值班** | **强制** (UX 攻坚 1 号动员令 § 1) | ☐ @ux |
| **前端值班** | 涉及 Vue / 前端代码 | ☐ @fe |
| **后端值班** | 涉及 Java / SQL | ☐ @be |
| **测试值班** | 涉及核心流程 | ☐ @qa |
| **运维值班** | 涉及发布 / 监控 | ☐ @ops |
| **R6 团队任意角色** (UX 攻坚 1 号动员令 § 三) | 涉及 a11y / 原型 / 视觉 / 可用性 | ☐ @r6 |

---

## 四、变更等级 (强建议披露, 非阻断)

- **P0-above** (UX 宪法违反 / 监管不可抗力): 0
- **P0** (生产事故 / 安全): 0
- **P1-C** (客户感知问题): 0
- **P1-R** (用户流失风险): 0
- **P2-R** (美观优化): 0
- **P3-R** (建议): 0
- **P1-I** (内部基础设施, 无用户感知): 默认选此项 (本次 PR 涉及文档/治理类变更)

---

## 五、回顾清单 (CI 7/7 通过)

- [ ] backend (Java + Maven compile + test) — 0 失败
- [ ] frontend (Vue + npm + lint + build) — 0 失败
- [ ] docker — 0 失败
- [ ] e2e — 0 失败
- [ ] monitoring-lint — 0 失败
- [ ] Trivy Scan — pass
- [ ] auto-approve (Bot 签 CI 通过证明) — pass

---

## 六、UX 至上铁律自检 (开发者必答)

填写以下, 如有"是"或"不确定", 必须升级到 UX 值班:

- [ ] 本 PR 是否引入了**任何**用户体验降级? (response time / 错误率 / 流程增多 / 强制操作等)
- [ ] 本 PR 是否会让**老年用户/视障用户/运动障碍用户**更难使用?
- [ ] 本 PR 是否在时间紧迫下, 简化了某个原本重要的体验?

---

## 七、关联治理文档

- `docs/governance/ux-governance/EXECUTIVE-PRINCIPLE.md` — UX 至上铁律宪法
- `docs/governance/ux-governance/L0-MANIFESTO.md` — UX 攻坚 1 号动员令
- `docs/governance/ux-governance/templates/a11y-acceptance-checklist.md` — 完整 a11y 模板 (本文件简化版)
- `docs/governance/ux-governance/01-metrics-standard.md` — 5 维量化阈值 + 10 项核心度量
- `docs/governance/ux-governance/02-weekly-review-ritual.md` § 7 — 一票否决清单 (L0 UX)
- `docs/governance/ux-governance/03-feedback-sla.md` — 反馈 SLA + 工单分级
- `docs/governance/ux-governance/rostering.md` § R6 — UX 攻坚 1 号动员令 6 角色团队

---

## 八、6 触点评审门禁 (UX 攻坚 1 号动员令 § 4)

> 本 PR 修改涵盖哪些触点, 对应 reviewer 必须签字 (按需).

| 触点 | 触发条件 | 必须 reviewer | 说明 |
|------|---------|--------------|------|
| **1. 原型评审** | 涉及新流程 / 信息架构 | 交互设计师 (@ix) | [L0-MANIFESTO.md § 二 #4](./docs/governance/ux-governance/L0-MANIFESTO.md) |
| **2. 视觉感知测试** | 涉及新视觉 / 品牌 / 主题 | 视觉设计师 (@visual) | 老年字号 ≥18px / 对比度 ≥4.5:1 |
| **3. 开发可用性** | 涉及 Vue / 交互代码 | 前端开发 (@fe) | a11y + 性能 (依 [a11y checklist](./docs/governance/ux-governance/templates/a11y-acceptance-checklist.md)) |
| **4. 灰度测试** | 涉及新功能 / 用户流程变更 | 可用性测试专家 (@ua) + 客服值班 (@cs) | 任务完成率 ≥95% |
| **5. 上线放行** | 涉及发布 / 灰度开关 | 项目总负责人 (@lead) + UX 值班 (@ux) | [02 § 7 一票否决清单 7 项](./docs/governance/ux-governance/02-weekly-review-ritual.md) |
| **6. 反馈收集** | 任意 UX-感知 PR | 客服值班 (@cs) | 须将相关功能加入 [feedback-pool.md](./docs/governance/ux-governance/feedback-pool.md) 工单跟踪 |

---

**Auto-merge 由 Bot 决策, 需所有勾选 + CI 7/7 PASS + UX 一票否决未触发才能 merge.**
