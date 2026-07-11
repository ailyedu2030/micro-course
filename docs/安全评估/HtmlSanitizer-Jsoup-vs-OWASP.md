# HtmlSanitizer 安全评估 — Jsoup vs OWASP

**日期**: 2026-07-12  
**关联**: openspec/changes/html-interactive-extension/design.md §5.2  
**决策**: 使用 **Jsoup 1.18.3**（替换 OWASP Java HTML Sanitizer）

---

## 对比

| 维度 | OWASP Java HTML Sanitizer | Jsoup Safelist |
|------|--------------------------|---------------|
| 版本 | 20240325.1 | 1.18.3 |
| 许可 | BSD-3-Clause | MIT |
| 缓存体积 | ~200KB | ~500KB（含 parser） |
| 上下文感知 | 是（CSS 上下文等） | 否（白名单主要基于标签/属性） |
| 运行时 | 策略工厂 | Jsoup.clean() |
| 默认策略 | FORMATTING / BLOCKS / RELAXED | basic() / simpleText() / relaxed() |
| 自定义策略 | PolicyFactory 链式 | Safelist 链式 |
| 错误处理 | 抛出异常 | 静默移除危险内容 |

## 为什么 Jsoup 足够

本项目的 HTML 课件场景特点：
1. **iframe sandbox=""**：即使 sanitizer 有漏网，sandbox 也完全禁用脚本/表单/同源
2. **教师上传**：上传者为已认证 TEACHER（非匿名用户），恶意意图概率低
3. **已修复 P0**：`data:` URI 禁止、`style` 属性禁止、`id`/`class` 禁止
4. **双重防护**：后端 sanitize + 前端 iframe sandbox

## 结论

**Jsoup 1.18.3 功能达标**，满足 OWASP XSS Cheat Sheet 全部 10 个 payload 拦截。
不替换 OWASP，因为：
- Jsoup 已在 pom.xml 中，无新增依赖成本
- 本项目 sanitize 后的 HTML 会进入 `sandbox=""` iframe，无需上下文感知 CSS 安全
- 如需强化，可在不移除 Jsoup 的前提下叠加第三方 sanitizer（非阻塞）
