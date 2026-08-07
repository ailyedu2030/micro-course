# R4b 产品/UX 独立评审报告（总工程师亲核）

> 评审：总工程师（子代理 R4b 未产出，改由总工程师按同一矩阵亲核）
> 日期：2026-08-06
> 评审对象：`docs/design/2026-08-06-PPT-HTML-音频同步控制方案.md` §1 目标 / §3 / §7-10 / §11 / §13.4
> 依据：现状代码阅读（SlidePlayer/SlideManage/CoursewareWorkbench/ScriptEditor/AudioManager/PptFlowEditor）+ 项目 UX 宪法

## 结论摘要

**T1/T2/T3 三个设计目标方向全部成立，但 P0 前必须并入 4 项体验闭环（U-2/U-4/U-6/U-7）与 2 项增强（U-5/U-8）。** 无 P0 级 UX 阻塞；关键 P1-C 为「PPT 页音频状态对学生不可见」与「HTML 无 marker 退化缺失」。

## U 矩阵逐项结论

| # | 检查项 | 结论 | 判断依据 | 级别 |
|---|--------|------|---------|------|
| U-1 | T1 PPT 驱动播放 | **PASS（需 U-2）** | 现状已有「页音频 + autoMode 自动翻页 + 倒计时」；P1 flow 求值补齐后达成"PPT 驱动"。手动翻页当前行为=切页即播（goTo→loadAudio→playAudio），符合讲述型课件直觉；点击舞台关闭自动模式（handleStageClick）保留 | - |
| U-2 | **PPT 页音频状态不可见** | **需修订（并入 R-3）** | `SlidePlayer.vue` `audio-status-bar` 仅 `v-if="contentType==='HTML_DIRECT'"`；PPT 页 PENDING/ERROR/无音频时学生零提示、播放按钮禁用无原因 | P1-C |
| U-3 | T2 HTML 驱动播放 | **PASS（需 U-4/U-6）** | 「点击段跳转 + active 高亮 + 进度条段边界 + 段间自动续播 + 互动段停留」为当前最优形态；D9/D1 修复后链路才真正可用 | - |
| U-4 | **HTML 无 marker 退化缺失** | **需修订（并入 R-7）** | 方案 §8.2 未明确"无标题/段落锚点 → 退化为整页单音频（legacy merged 已覆盖）"，否则会出现"分段为空但不播" | P1-C |
| U-5 | 生成音频后反馈 | **需修订（并入 R-10）** | `AudioManager.handleGenerate` 提交后仅手动刷新；建议 3s 轮询直至 READY/FAILED，并展示进度（复用 v2 audio status） | P2 |
| U-6 | 完成态缺失 | **需修订（并入 R-10）** | 全部页播完 / 单页课件 ended 时无"本课学习完成"反馈；需补结束态（可复用 markSlideComplete + 成功提示） | P1-C |
| U-7 | a11y | **需修订（并入 R-10 局部）** | 段高亮元素需 `tabindex`/`aria-current`；解锁按钮补 aria-label；现有 focus-visible 已全覆盖父页控件；iframe 内容 Tab 焦点缺口以"父页提供可聚焦的段列表/进度条"缓解（P2 可选） | P2 |
| U-8 | 教师预览一致性 | **PASS** | `SlidePreview.vue` 直接复用 SlidePlayer，教师所见=学生所见；非 STUDENT 不上报进度（F-06-03 守卫）不影响播放体验；预览中 flow/高亮逻辑同链路自然生效 | - |

## 工作流（T3）评审

1. 教师路径"上传 → AI 讲述稿 → 生成音频 → 预览"在 v2 工作台为四面板（内容/讲述稿/音频/跳转），步骤 ≤3 步：满足 L0 UX 宪法；v1 旧版同路径可用。
2. `ScriptEditor` AI 生成从 mock 改真实后端后，"预览→应用此版本→保存（新版本）→生成音频"交互闭环顺畅；保存前须修复 D2（useUserStore 未导入必崩）。
3. 音色下拉改由后端 `tts-options` 渲染后，教师看到的是 MiniMax 真实音色中文名（如"女声·少女"），消除 male-young 等无效选项造成的"生成了但声音不对/失败"困惑。
4. 生成中反馈：除 3s 轮询外，建议 GENERATING 时禁用重复提交（防重复计费），FAILED 显示原因（余额/限流/超时）。

## 验收标准可测性

- P0/P1/P2 验收均可自动化：单测（协议消息/flow 求值/origin 守卫）+ ego-browser 真实交互复测（PPT 自动翻页、HTML 段点击跳转、高亮、播放失败提示）。
- 建议补验收：① 无音频页（PENDING/ERROR）提示可见；② 单页课件完成态触发；③ 教师预览不产生学习进度；④ 移动端 375px 播放器布局；⑤ 弱网音频加载失败可重试。

## 体验倒退风险评估

| 风险 | 评估 | 缓解 |
|------|------|------|
| v1 旧 HTML 课件（含 AUDIO_SEG_ 占位符/注入脚本） | 现注入脚本已语法损坏（D1），修复后体验反而恢复 | 兼容桥双发（§6.4）；新上传一律 v2 |
| 多段音频预加载 | 单页多段若全量预取浪费流量 | 只预取下一段元数据（H-2）；音频走 token URL + 1h 缓存 |
| autoMode 默认开启 | 首次进入被 autoplay 拦截→静默失败 | 解锁层：未解锁显示"点击开始"，解锁后自动续播（R-4） |
| flow BRANCH/SKIP 突然生效 | 学生跳页困惑 | 页点条显示路径标记（可跳过页加图标）；P1 先默认 NEXT，规则灰度启用 |

## 最终结论

方案达成用户核心诉求（PPT/HTML 控制播放 + MMX 链路闭环）且不引入体验倒退；P0 实施需并入 R-3（PPT 状态栏）、R-7（无 marker 退化）、R-10 完成态，并保留 R-4（解锁层）与 R-5（flow evaluate）。UX 维度合格。
