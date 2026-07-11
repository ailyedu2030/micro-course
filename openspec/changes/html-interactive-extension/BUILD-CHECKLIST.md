# Build 分身自检 Checklist（用户要求补交）

> **来源**：professor 分身审计分支 `feature/html-interactive-extension` 后，发现 build 未交付自检报告。
> **目的**：build 必须按本清单逐项填写并交付，**用户拒绝基于"看着像能跑"接受结果**。
> **强制**：5 维交叉验证（R1-R5）+ 本清单全部勾选 + 出示证据，才能进 PR 流程。

---

## 0. 当前状态（教授分身审计时所见，已记录）

| 项 | 状态 | 备注 |
|---|---|---|
| 分支 | `feature/html-interactive-extension` | ✅ 已建 |
| Modified | 17 文件（后端 7 / 前端 6 / 测试 1 / 配置 3） | |
| Untracked | V177 migration + `util/HtmlSanitizer.java` + 测试目录 + ~~fix_impl.py~~ | ⚠️ fix_impl.py 已删（违规） |
| Commit 数 | **0** | ❌ 未提交 |
| R1-R5 报告 | **未交付** | ❌ 本清单要补的就是这个 |

---

## 1. 必填：5 维交叉验证 R1-R5

### R1 · 编译验证
- [ ] `bash scripts/local-dev-deploy.sh` → **16/16 通过**（截图或粘贴输出）
- [ ] `mvn -pl micro-course-api compile` → BUILD SUCCESS
- [ ] `npm --prefix micro-course-admin run build` → 0 errors
- [ ] **禁止用 Python 改 Java 代码**（已删除的 fix_impl.py 不可复活）

### R2 · 单元测试
- [ ] `mvn test` → 全部通过，无 skip、无 error
- [ ] 新增的 `SlideServiceTest.java` 必须真跑过（粘贴 mvn test 输出的该测试类名）
- [ ] **覆盖率 ≥ 80%**（新增代码行），截图 jacoco report

### R3 · 数据库变更
- [ ] V177 migration 在本地 PostgreSQL 跑过一次（粘贴 `flyway info` 输出）
- [ ] V177 可回滚（写一个 V178__rollback.sql 备用）
- [ ] **生产环境未执行 V177**（未 ssh、未 psql、未 Flyway migrate 到生产）

### R4 · 权限 / 安全
- [ ] `/upload-html` 端点有 `@PreAuthorize` + 业务层 `verifyOwner` 双校验
- [ ] HtmlSanitizer 通过 OWASP XSS Cheat Sheet 至少 10 个 payload 测试
  - [ ] `<script>alert(1)</script>`
  - [ ] `<img src=x onerror=alert(1)>`
  - [ ] `<a href="javascript:alert(1)">x</a>`
  - [ ] `<svg/onload=alert(1)>`
  - [ ] `<iframe src=javascript:alert(1)>`
  - [ ] `<form><input type=hidden name=x value="><script>alert(1)</script>">`
  - [ ] `<style>@import 'javascript:alert(1)';</style>`
  - [ ] `<meta http-equiv="refresh" content="0;url=javascript:alert(1)">`
  - [ ] `<base href="javascript:">`
  - [ ] `<embed src=javascript:alert(1)>`
- [ ] 5MB 限制在后端 + 前端双校验（粘贴两层代码位置）
- [ ] SQL injection 防护：参数都用 `#{}` 或 `?` 占位符（粘贴 grep 结果）

### R5 · 业务逻辑
- [ ] PRD 中每个验收标准 → 对应实现文件 + 行号
- [ ] 异常路径覆盖：HTML 为空 / 超 5MB / 全被消毒 / 课程不存在 / 无权限 / 章节不属于该课程
- [ ] 前端错误提示与后端 ErrorCode 16009-16012 对齐（粘贴对照表）

---

## 2. 必填：Conventional Commit + Branch 规范

- [ ] commit message 格式：`<type>(<scope>): <subject>`
  - 例：`feat(slide): add HTML_DIRECT content type with sanitization`
- [ ] 单次 commit 或拆分为逻辑清晰的多个（不允许一坨 17 文件 single commit）
- [ ] commit body 必须有：
  - `PRD-ref: openspec/changes/html-interactive-extension`
  - `Rollback: <具体回滚步骤>`
- [ ] GPG sign-off（如项目要求）

---

## 3. 必填：根因分析（如果之前出过错）

> 教授分身审计时看到 `fix_impl.py` 第 7 行有 `git checkout -- SlideServiceImpl.java`（自回滚），说明 build 中途犯过错。

- [ ] 出错原因（具体到代码位置）
- [ ] 为什么一开始出错（root cause，不是表面）
- [ ] 横向扫描：同类型问题有没有别处（grep 列出）
- [ ] 修复后是否重跑 R1-R5

---

## 4. 必填：PR 准备

- [ ] 标题：`feat(slide): HTML 互动课件扩展（html-interactive-extension）`
- [ ] 描述含：
  - 改了什么（1 段）
  - 为什么改（PRD 链接）
  - 怎么测（5 维验证截图）
  - 影响范围（哪些接口、哪些表、哪些前端页面）
  - 回滚方案（具体步骤，不超过 3 行）
- [ ] 不允许 self-approve
- [ ] reviewer 至少 1 人

---

## 5. 必填：交付清单（build 必须粘在最终回复里）

```
✅ R1 编译：mvn + npm 全绿（粘贴 mvn compile 末尾 5 行 + npm build 末尾 5 行）
✅ R2 测试：mvn test 全过（粘贴 BUILD SUCCESS + Tests run 行）
✅ R3 DB：V177 在本地跑过（粘贴 flyway info 第 17 行）
✅ R4 安全：10 个 XSS payload 全部被挡（粘贴 HtmlSanitizerTest 输出）
✅ R5 业务：6 个异常路径全测过（粘贴测试用例名列表）
✅ Commit：<sha> <message>
✅ Push：<remote-url>
✅ PR：<github-pr-url>
```

---

## 6. 红线（任何一条触发 = P0 事故，立即停止）

- ❌ 再次出现 fix_impl.py 这种"用 Python 改 Java"的脚本
- ❌ 跳过 R1-R5 任一项就说"完成"
- ❌ 把改动推到 main 分支
- ❌ 在生产 ssh / curl / Playwright 调试（参见 AGENTS.md P0 铁律第 2 条）
- ❌ commit message 不带 Rollback 步骤
- ❌ 没有 reviewer 就 merge

---

## 7. 用户已知风险（教授分身审计标注）

| 风险 | 文件 | 说明 | build 必须给出处理方案 |
|---|---|---|---|
| 🟡 data: URI 允许 | `HtmlSanitizer.java:45` | 允许 base64 内嵌图片，5MB 内可塞死重 | 是接受 / 还是限制 base64 ≤ 100KB？ |
| 🟡 util/ 目录不在 PRD 里 | `plugin/interactive/util/` | 新增包路径 | 解释为什么不放 service 包内 |
| 🟡 V177 没回滚脚本 | `db/migration/V177` | 不可逆 DDL 缺回滚 | 必须补 V178__rollback.sql |

---

**最后**：本清单由 professor 分身审计后要求 build 必须补交。build 跑完后，**在最终回复里粘贴第 5 节交付清单的全部 8 行**——这是用户验收的唯一标准。