# R3b Web 标准/浏览器行为复核报告（总工程师独立取证）

> 评审：总工程师（子代理 R3b 未完成，改由总工程师按同一矩阵亲核）
> 日期：2026-08-06
> 评审对象：`docs/design/2026-08-06-PPT-HTML-音频同步控制方案.md` §13.3 W-01~W-06 与 §9 Autoplay 解锁设计
> 方法：真实浏览器实测（Playwright + Chromium）+ 官方标准引用（WHATWG / MDN）

## 结论摘要

**6 项检查全部通过（含 2 项实证复验），协议 v2 与 Autoplay 解锁设计符合浏览器硬约束，可落地。** 无 P0/P1-C 阻塞项；2 项 P2 增强建议（H-1/H-2）。

## W 矩阵逐项结论

| # | 检查项 | 结论 | 证据 | 级别 |
|---|--------|------|------|------|
| W-01 | Autoplay 检测与解锁 | **PASS** | `navigator.getAutoplayPolicy()` 属「Autoplay Policy Detection API」，MDN 标 experimental/limited availability；方案 §9 以 `audio.play().catch(e=>e.name==='NotAllowedError')` 为主力、getAutoplayPolicy 仅渐进增强，正确。sandbox iframe 内点击**不构成父页用户激活**（激活链不跨 window），父页回 `blocked` + 提示为合理兜底。iOS Safari 对无手势音频更严，方案统一"点击开始"兼容 | - |
| W-02 | D9 origin 序列化与修复 | **PASS（实证）** | Chromium 实测：sandbox srcdoc iframe 消息 `event.origin` = 字符串 `"null"`（typeof string），`"null" !== null` 恒 true → 现守卫拒收全部消息。修复 `event.origin === 'null'` 正确；WHATWG html#3585 官方确认 opaque origin 序列化为 "null"。**增强建议 H-1**：同页存在多个 sandbox iframe 时 origin 无法区分来源，建议叠加 `event.source === iframe.contentWindow` | **P0（修复项，方案 R-1）** |
| W-03 | timeupdate 频率与节流 | **PASS** | MDN HTMLMediaElement.timeupdate_event：频率约 4-66Hz（随负载变化）。方案 §6.2 父→iframe `time-update` 节流 ~4Hz 合理，避免消息洪水 | P2 |
| W-04 | MediaSync 与自研高亮路线 | **PASS** | W3C MediaSync/SyncMedia 无浏览器实现（规范讨论阶段）；自研「CSS active 高亮 + postMessage 驱动」是唯一务实路线。Reveal.js audio-slideshow（每页一音频+翻页驱动）、H5P Course Presentation（每页音频+回归从头播）、player.js（iframe postMessage 协议：ready 握手/方法/事件/监听器关联）与本方案协议 v2 设计一致，无冲突 | - |
| W-05 | Permissions-Policy autoplay | **PASS** | 默认 policy `self`：iframe 内媒体自动播放受父页策略限制 → 进一步印证「iframe 不宿主音频、父页单宿主」决策 | - |
| W-06 | 单 audio 切换与预加载 | **PASS** | 同一 `<audio>` 换 `src` 会中断当前播放 → 段间切换须先 pause/清空再 load（方案 §8.1 已含）；预加载下一段建议独立 `preload='auto'` 探测元素或 `Audio()` 预取（H-2）。token URL 带 `Cache-Control: max-age=1h`（TtsController），seek 依赖服务端 Range 支持（nginx 静态直发默认支持） | P2 |

## 补充复核结论

1. sandbox iframe→父 `postMessage(msg,'*')`：opaque origin 下父页只能以 origin==="null" 收信；`'*'` 是唯一可行 targetOrigin，配合 source 校验即可防同页多 iframe 混淆（H-1）。
2. 父→iframe 可达性：sandbox="allow-scripts" 不阻断 postMessage 收发，实测父→iframe 消息可正常送达。
3. autoplay 只拦截 `play()`，不拦截 `seek()`/`load()`：解锁前可预载并定位，解锁后即刻起播（方案 §9 已利用）。
4. 多段顺序播放衔接：浏览器无 gap-less 保证，方案 0.5s 段间停顿 + 进度提示为可接受体验；精确衔接可后续用 WebAudio 缓冲（P3 增强项，不阻塞）。

## 修订建议

- **H-1（并入 R-1）**：origin 守卫修复为 `event.origin === 'null' && event.source === currentIframe?.contentWindow`，双条件防同页多 iframe 伪消息；单测覆盖（origin="null"+source 匹配 放行 / 其他 origin 拒绝 / source 不匹配 拒绝）。
- **H-2（并入 P2）**：下一段音频用独立 `Audio()` 预取元数据（duration），段切换时已有时长信息，进度条无需等待 loadedmetadata。

## 最终结论

协议 v2 的 origin 校验、消息节流、Autoplay 解锁层均符合浏览器约束；D9 修复方案（R-1 + H-1）完备。Web 标准层面**无阻塞项**，可按方案进入 P0 实施。
