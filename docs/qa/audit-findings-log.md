# 全页面审查 · 问题发现-修复日志

> 每条记录按"根因分析五段式"：症状 / 直接原因 / 根本原因 / 横向扫描 / 防止再发。
> 与 `full-page-verification-matrix.md` 配套维护。

## 2026-08-04 · 学生答题题型渲染与评分链路（E8.1 / C6.2 补测）

### F-2026-08-04-01 · 判断题无选项渲染，学生无法作答（P1-C）

- **症状**：练习 10 第 3 题（判断题"0是自然数"）作答区空白，无"正确/错误"选项，无法作答。
- **直接原因**：答题页 `ExerciseTake.vue` 单选/判断分支按 `currentQuestion.options` 循环渲染选项，题目 9 的 `options` 字段为空（`null`）。
- **根本原因**：判断题创建链路缺失默认选项——`QuestionList.vue` 表单只提供"正确答案"radio（正确/错误），`formData.options` 始终为 `''`；后端 `QuestionServiceImpl.create/update/batchImport` 也未对 JUDGE/TRUE_FALSE 归一化默认选项。任何经 UI 创建的判断题都是"不可作答"的残次题。
- **横向扫描**：教师预览组件 `QuestionPreview.vue` 对判断题硬编码"正确/错误"（不受影响）；答题页 H5 分支同源缺陷；导入 Excel 判断题空选项同样受影响。
- **修复**：后端 create/update/import 统一 `normalizeJudgeOptions` 补默认选项 `[{"value":"true","label":"A","text":"正确"},{"value":"false","label":"B","text":"错误"}]`；`QuestionList.vue` 创建/编辑提交判断题时强制写入默认选项；`ExerciseTake.vue` 归一化时对 JUDGE 空选项兜底注入。已复测：判断题渲染"正确/错误"并正确判分。

### F-2026-08-04-02 · 简答题（SHORT_ANSWER）无作答区（P1-C）

- **症状**：练习第 1 题"简述极限的定义"（简答题）作答区空白，无输入框，无法作答，提交时提示"未作答"。
- **直接原因**：`ExerciseTake.vue` 桌面与 H5 两处模板仅处理 SINGLE/JUDGE/MULTIPLE/FILL/ESSAY，缺少 SHORT_ANSWER 分支。
- **根本原因**：题型枚举含 SHORT_ANSWER（后端校验白名单、教师预览组件均支持），但答题页渲染分支遗漏，属"类型支持矩阵不一致"缺陷。
- **横向扫描**：`QuestionPreview.vue` 简答题有 textarea（正常）；结果解析页 `formatUserAnswer` 对 SHORT_ANSWER 走 `answers[qId]` 文本路径（正常）；仅答题输入区缺失。
- **修复**：`ExerciseTake.vue` 两处模板将 SHORT_ANSWER 并入 FILL 的 textarea 分支（rows=3，placeholder"请输入您的答案"）。已复测：简答题可输入并随提交持久化。

### F-2026-08-04-03 · 多选题答案格式契约不一致 → 必判错（P1-C）

- **症状**：多选"以下哪些是偶数？"作答 A.2+C.4（正确答案 2,4），结果显示答案"2、4"但判错，选项标记 `option-wrong`，得分 20（正确答案 30）。
- **直接原因**：前端提交 `JSON.stringify(multipleAnswers.sort())` → `["2","4"]`；后端 `gradeQuestion` 对 MULTIPLE 用 `userAnswer.split(",")` 解析 → 元素带引号 `["2"`、`"4"]`，与正确答案集合 `{2,4}` 永不相等 → 判错/部分得分 0。
- **根本原因**：多选答案格式契约未统一：题库创建端答案存纯逗号分隔值（`correctOptions.join(',')`），学生答题端提交 JSON 数组，后端解析只支持逗号分隔。
- **横向扫描**：前端 `parseMultipleAnswer` 仅 `JSON.parse`，编辑端创建的多选（答案 `"2,4"`）解析失败返回空数组 → 结果页"正确答案"映射失败、`isMultipleCorrect` 全 false；同一缺陷影响客户端判分与高亮。
- **修复**：后端新增 `parseMultipleAnswerSet` 兼容 JSON 数组与逗号分隔两种格式；前端 `parseMultipleAnswer` JSON 解析失败时回退 `split(',')`。已复测：多选判正确，得分 30、答对 3/5、选项高亮 option-correct。
