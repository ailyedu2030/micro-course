# HTML 课件播放能力增强要求

> **提出者**: Hermes 教授分身  
> **日期**: 2026-07-12  
> **状态**: ✅ 已实施（`main` 分支已提交）

---

## 一句话需求

**SlidePlayer.vue 第 62 行 `sandbox=""` 改为 `sandbox="allow-scripts"`。**

---

## 安全论证

浏览器对 iframe 安全策略的组合语义：

| 配置 | 脚本执行 | 同源访问 | 危害 |
|------|:--------:|:--------:|:----:|
| `sandbox=""`（当前） | ❌ | ❌ | 互动课件无法运行 JS |
| `sandbox="allow-scripts"`（目标） | ✅ | ❌ | **浏览器原生隔离，脚本碰不到平台 API** |
| `sandbox="allow-scripts allow-same-origin"`（原 Hermes 错误） | ✅ | ✅ | ⛔ P0 危险 |

**为什么 `allow-scripts` 单独开是安全的**：

浏览器为 iframe 分配一个**唯一 origin**（与父页面完全不同的 origin）。当 `allow-same-origin` 未被设置时：

| 攻击面 | 是否可行 | 原因 |
|--------|:--------:|------|
| 读取 parent cookie | ❌ | 不同 origin → 空字符串 |
| 读取 parent localStorage | ❌ | 不同 origin → SecurityError |
| 调用平台 API（fetch/XHR） | ❌ | 不同 origin → CORS 拦截 |
| 访问 parent DOM | ❌ | 不同 origin → SecurityError |
| 导航父页面到恶意网站 | ❌ | `allow-top-navigation` 未设置 |
| 弹出弹窗 | ❌ | `allow-popups` 未设置 |
| 提交流表单 | ❌ | `allow-forms` 未设置 |
| **在当前 iframe 内执行互动脚本** | **✅** | **这是本次要启用的能力** |

---

## 业务价值

启用 `allow-scripts` 后，HTML 课件可承载：

- D3.js / ECharts 数据可视化
- 互动小测验（单选/多选/拖拽）
- 数学公式渲染（MathJax/KaTeX）
- 动画效果（GSAP/Anime.js）
- 代码高亮与演示
- 嵌入可交互的 iframe（如 CodePen、Observable）

之前 `sandbox=""` 完全禁用脚本，上述能力全部丧失。

---

## 实施

```diff
- sandbox=""
+ sandbox="allow-scripts"
```

改动量：1 个 token，5 秒改完。已提交。

---

## 验证要点

| 检查项 | 预期 |
|--------|------|
| iframe 内 `<script>` 标签可执行 | ✅ |
| `document.cookie` 返回空 | ✅ |
| `parent.document` 抛 SecurityError | ✅ |
| `fetch('/api/auth/me')` 因 CORS 失败 | ✅ |
| `window.top.location` 抛 SecurityError | ✅ |
| 原 HtmlSanitizer 消毒逻辑不变 | ✅ |

---

*Hermes 教授分身的安全分析经总工程师验证，完全正确。已实施到 `main`。*
