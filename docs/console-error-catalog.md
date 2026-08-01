# Console 错误诊断目录 (非应用 Bug 清单)

> **目的**: 当用户/开发者/QA 在生产前端看到 console 错误时, **快速判断**该错误是:
> - ✅ 应用 bug → 走修复流程 (PR + 部署)
> - ⚠️ 浏览器/扩展噪音 → 知道**不要**修, 直接告知用户
> - ❓ 难以判断 → 走诊断流程

> **最后更新**: 2026-08-01 (PR #165 + #166 部署后)
> **维护者**: 总工程师 (opencode)

---

## 1. 识别应用 bug vs 浏览器/扩展噪音

### 应用 bug 特征 (✅ 必修)

| 特征 | 示例 |
|------|------|
| **文件名从我们的 dist 出发** | `index-BbJXRHKC.js:2:7018` (我们的 bundle) |
| **错误类型是 Vue/JS 运行时** | `TypeError`, `ReferenceError`, `AxiosError`, `SyntaxError` |
| **涉及我们的 API 调用** | `axios.post /api/auth/refresh`, `axios.get /api/auth/me` |
| **错误信息有业务语义** | `failed to sync from backend`, `getInfo failed`, `认证失效` |
| **前端 console 出现 `XXX 401` 或 `XXX 415` 错误** | 后端 API 响应错误 |

### 浏览器噪音特征 (❌ 不修)

| 特征 | 常见来源 |
|------|---------|
| **文件名是 `content_main.js` / `content_guard.js`** | Microsoft Edge 浏览器内部脚本 |
| **文件名是 `chrome-extension://xxx`** | Chrome/Edge 浏览器扩展 |
| **文件名是 `moz-extension://xxx`** | Firefox 浏览器扩展 |
| **文件名是 `safari-web-extension://xxx`** | Safari 浏览器扩展 |
| **错误信息是浏览器 API 通知** | `LanguageDetector`, `Translation`, `Immersive Reader` |
| **错误信息是浏览器扩展内部错误** | `Immersive Translate`, `Surfingkeys`, `Vimium`, `Tampermonkey` |

---

## 2. 已知的浏览器/扩展噪音模式

### 2.1 Microsoft Edge 浏览器 (`content_main.js`)

**症状**: `content_main.js` 文件 + 浏览器内部 API 通知

| 错误 | 原因 | 处理 |
|------|------|------|
| `This page uses the built-in LanguageDetector feature` | Edge 内置 LanguageDetector API | ❌ 不修 (浏览器特性) |
| `[Intervention] Images loaded lazily and replaced with placeholders` | Edge 优化 lazy load 图片 | ❌ 不修 (HTML 标准, UX 性能) |
| `content_main.js:13250 FA.isTriggerKey TypeError: Cannot read properties of undefined (reading 'toLowerCase')` | 浏览器扩展冲突 (Surfingkeys 等键盘快捷键扩展) | ❌ 不修 (扩展 bug) |

**Surfingkeys 错误根因**:
- Surfingkeys 是浏览器键盘快捷键扩展 (Chrome/Edge)
- 内部 `isTriggerKey` 函数: `event.key.toLowerCase()`
- 当 `event.key` 是 `undefined` 时 (例如某些键组合, IME, 死键) → TypeError
- 5 次重复 = 用户多次按键盘触发

**用户操作**:
1. 临时禁用 Surfingkeys 扩展 (Edge → 扩展 → 关闭)
2. 反馈给 Surfingkeys 项目 (GitHub: https://github.com/brookhong/Surfingkeys/issues)
3. 用其他浏览器测试 (Chrome/Firefox/Safari)

### 2.2 沉浸式翻译 (Immersive Translate) 浏览器扩展

**症状**: `content_main.js:4889 Immersive Translate ERROR: sync rules error: fetchError`

**根因**: 沉浸式翻译扩展尝试从 `https://api.immersivetranslate.com/` 同步翻译规则, 但 fetch 失败

**处理**:
- ❌ 不修 (第三方扩展)
- 用户操作: 暂停沉浸式翻译 → 重试 → 反馈给扩展作者

### 2.3 Edge Translator / Google Translate 内置

**症状**: `Translation` API 通知或 `page translated` 通知

**处理**:
- ❌ 不修 (浏览器内置)
- 用户操作: 在 Edge 设置中关闭自动翻译

### 2.4 Tampermonkey 用户脚本

**症状**: `chrome-extension://...` 或 `moz-extension://...` 错误

**处理**:
- ❌ 不修 (用户脚本)
- 用户操作: 禁用 Tampermonkey → 重试

---

## 3. 已知应用 bug 历史

### 3.1 真实应用 bug (已修复)

| Bug ID | 描述 | 文件 | PR | 部署日期 |
|--------|------|------|----|----|
| Bug A | `vite.config.js` 缺 `preview.proxy` | `vite.config.js` | #161 | 2026-07-31 |
| Bug B | element-plus 2.14.x 移除 `ElMessage.config()` | `main.js` | #161 | 2026-07-31 |
| Bug C | course-crud e2e selector 硬编码 i18n 不匹配 | `tests/e2e/course-crud.spec.ts` | #161 | 2026-07-31 |
| Bug D | `student/CourseDetail.vue` HTML_COURSEWARE 章节 iframe 预览 | `views/student/CourseDetail.vue` | #161 | 2026-07-31 |
| Bug E | `/api/api` 路径重复 (3 个 api 文件) | `plugins/interactive/api/{html,ppt,query}Courseware.js` | #163 | 2026-07-31 |
| Bug F | element-plus el-radio deprecation `label` → `value` | 3 个组件 | #163 | 2026-07-31 |
| Bug G | `/api/auth/refresh` 415 (axios 0.27+ 行为变更) | `utils/request.js` | #165 | 2026-08-01 |
| Bug H | enums fallback 路径 `console.warn` 噪音 | `utils/enums.js` | #165 | 2026-08-01 |

### 3.2 真实应用 bug 模式 (横向扫描)

| 模式 | 触发 | 修复 |
|------|------|------|
| **axios 0.27+ 行为变更** | 显式 `headers: {}` 不自动注入 Content-Type | 显式 `headers: { 'Content-Type': 'application/json' }` |
| **baseURL 重复** | baseURL 含 `/api` + 调用 URL 也含 `/api/` → `/api/api/...` | 调用 URL 去掉 `/api/` 前缀 |
| **element-plus 弃用 API** | `el-radio label=...` 在 2.14.x 弃用, 3.0.0 移除 | `label=` → `value=` |
| **fallback 路径噪音** | `console.warn` 在 fallback 路径, 用户 console 噪音 | `console.debug` |

---

## 4. 诊断流程

### Step 1: 检查错误文件名

```js
// ✅ 应用 bug (我们的 bundle)
index-BbJXRHKC.js:2:7018
vendor-axios-DhXgJQ-f.js:3:10743

// ❌ 浏览器/扩展噪音 (不是我们的)
content_main.js:13250
content_guard.js:1
chrome-extension://abcdef/popup.js:1
moz-extension://uuid/background.js:1
```

### Step 2: 检查错误信息

```js
// ✅ 应用 bug (业务语义)
'AxiosError: Request failed with status code 401'
'failed to sync from backend'
'认证失效，停止轮询 401'
'ElementPlusError: [el-radio] [API] label...'

// ❌ 浏览器噪音 (浏览器/扩展特性)
'This page uses the built-in LanguageDetector feature'
'Immersive Translate ERROR: sync rules error'
'[Intervention] Images loaded lazily...'
```

### Step 3: 检查错误频率

```js
// 应用 bug 通常: 每次触发同一操作都出现
// 浏览器噪音: 一次性 / 偶尔 / 仅特定键组合
```

### Step 4: 应用 error handler 是否捕获

```js
// 在 App.vue / main.js 添加临时 console:
window.addEventListener('error', (e) => {
  console.log('APP ERROR HANDLER:', e.filename, e.message)
})

// 应用 bug: 我们的 bundle 文件被捕获
// 浏览器扩展: 通常不被捕获 (浏览器安全沙箱)
```

---

## 5. 用户报告 console 错误的标准回复模板

### 5.1 浏览器/扩展噪音

> "您截图的 console 错误是 **Surfingkeys 浏览器扩展**的内部 bug, 不是我们微课平台的问题。
> - `content_main.js` 是 Microsoft Edge 浏览器内部脚本名
> - `FA.isTriggerKey` 是 Surfingkeys 扩展的函数
> - 这是扩展本身的 bug (event.key 是 undefined 时 TypeError)
>
> 建议:
> 1. 临时禁用 Surfingkeys 扩展 (Edge → 扩展 → 关闭), 重试
> 2. 反馈给 Surfingkeys 项目: https://github.com/brookhong/Surfingkeys/issues
> 3. 切换浏览器测试 (Chrome/Firefox/Safari)
>
> 我们应用**不能修复**浏览器扩展 bug (浏览器安全沙箱阻止应用拦截扩展错误), 也不应该尝试 (会破坏扩展功能)。"

### 5.2 真实应用 bug

> "您截图的 console 错误是 **真实应用 bug**。已确认根因:
> - **Bug G**: `src/utils/request.js:125` refresh 调用 `headers: {}` 导致 axios 0.27+ 不自动注入 Content-Type → 后端 415
> - **修复**: `headers: { 'Content-Type': 'application/json' }` (1 行)
> - **PR**: #165
> - **部署**: 2026-08-01
> - **验证**: `curl -X POST .../auth/refresh -H 'Content-Type: application/json'` → HTTP 401 (token 错误), 不是 415
>
> 后续遇到类似问题, 可以对照本文档 [§3.1 真实应用 bug 历史] 确认。"

---

## 6. 防止再发

### 新增强制看板流程

任何 console 错误报告必须:
1. **先查文件名** (是 dist 还是 content_main.js)
2. **再查错误信息** (业务语义 vs 浏览器特性)
3. **再查应用 error handler 是否捕获** (host page vs 扩展 sandbox)
4. **再决定修复** (应用 bug → 修, 浏览器噪音 → 文档回复)

### precheck.sh 新增项 (TODO)

```bash
# 检测: 是否有未在已知应用 bug 列表中的新 console 错误
grep -rn "console.error\|console.warn" src/ --include="*.vue" --include="*.js" | head -20
# 比对: 是否在上方 [§3.1 真实应用 bug 历史] 列表里
# 输出: 新增 console 调用列表 (供 PR review)
```

### 监控告警

生产监控 (`docker logs micro-course-micro-course-api-1`) 关注:
- ✅ 我们的 bundle 名出现在错误堆栈 → 应用 bug
- ❌ `content_main.js` / 扩展 ID → 浏览器/扩展噪音, 不计入

---

## 7. 相关文档

- [docs/incidents/2026-07-31-pr161-deployment-p0.md](incidents/2026-07-31-pr161-deployment-p0.md) — P0 事故复盘
- [docs/deployment/PR161-DEPLOYMENT-RUNBOOK.md](deployment/PR161-DEPLOYMENT-RUNBOOK.md) — 部署 SOP
- [CHANGELOG.md](../../CHANGELOG.md) — 版本变更记录
- [ROLLBACK_PLAN.md](../../ROLLBACK_PLAN.md) — 回滚预案
- [PRODUCTION_SAFETY.md](../../.agents/skills/production-safety/SKILL.md) — 生产安全铁律

---

*Generated by*: 总工程师 (opencode)
*Date*: 2026-08-01
*Status*: 持续维护 (任何新发现的 console 错误类型都加入本文档)"