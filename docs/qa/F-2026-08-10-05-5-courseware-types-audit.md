# F-08-10-05 · 5 种课件类型独立管理 · 全栈审计与补全方案

## 用户指令（铁律）
> 课件类型分成：**视频课件、PPT 课件、HTML 课件、练习课件、线下课程**，同时**所有课件都有独立的管理页面**。
> 前端、后端、路由、功能控制、数据链等所有层面**确保没有任何错误和遗漏**。

## 现状矩阵（5 类型 × 7 层）

| 类型 | 后端枚举 | 后端 API | 前端 config | ADMIN 菜单 | ACADEMIC 菜单 | TEACHER 菜单 |
|------|---------|---------|------------|-----------|--------------|-------------|
| 视频课件 | `VIDEO` ✅ | VideoStream ✅ | "视频课程" ⚠️ | "视频管理" ⚠️ | **❌ 缺失** | "视频管理" ⚠️ |
| PPT 课件 | `PPT_COURSEWARE` ✅ | PptCourseware ✅ | "PPT 课件" ✅ | "PPT 课件" ✅ | **❌ 缺失** | "PPT 课件" ✅ |
| HTML 课件 | `HTML_COURSEWARE` ✅ | HtmlCourseware ✅ | "HTML 课件" ✅ | "HTML 课件" ✅ | **❌ 缺失** | "HTML 课件" ✅ |
| 练习课件 | ❌ 章节维度 | Question ✅ | (无 courseType) | "练习管理" ⚠️ | **❌ 缺失** | "练习管理" ⚠️ |
| 线下课程 | `OFFLINE` ✅ | OfflineSession ✅ | "线下课程" ✅ | **❌ 缺失** | **❌ 缺失** | "线下课堂" ⚠️ |

## 漏洞归类

### P0 · 结构性缺失
1. **ACADEMIC 完全无内容资源菜单**——教务无法访问 5 种课件管理页
2. **ADMIN 缺"线下课程"菜单**——admin 不能进入线下场次管理
3. **ADMIN/ACADEMIC 缺统一入口**：视频/PPT/HTML/练习都需补齐

### P1-C · 用户命名不一致（铁律 UX 至上）
4. "视频课程"（config + i18n + 学生端显示）→ 用户要求"视频课件"
5. "视频管理/练习管理/线下课堂"（菜单）→ 用户要求"视频课件/练习课件/线下课程"
6. `CourseDetail.vue` 注释"4 选 1：HTML 课件 / PPT 课件 / 视频课程 / 线下课程"硬编码

### P1-I · 文档/测试缺失
7. 后端 `CourseType` 枚举 4 值 + 注释"V333 简化方案"——需要文档说明"前端按 5 维度展示但后端 4 值枚举"

## 总工程师决策（按铁律自主决策 · 风险最低方案）

**后端不动**（V333 4 值 CourseType 枚举保留 + 注释更新明确 4→5 维度语义）：
- 课程维度：HTML_COURSEWARE / PPT_COURSEWARE / VIDEO / OFFLINE
- 章节维度：VIDEO / INTERACTIVE / OFFLINE / EXERCISE
- 练习作为"含练习章节的课程聚合"展示（保持章节维度，不新增枚举）

**前端补齐**：
1. `menuConfig.js` 三角色统一"X 课件"命名 + ACADEMIC 补内容资源组 + ADMIN 补"线下课程"
2. `courseTypeConfig.js` VIDEO 改"视频课件"（4 值保持，标签用户化）
3. `i18n/zh-CN.js` 同步 VIDEO → "视频课件"
4. `router/index.js` 新增 `/admin/offline-sessions` 路由 + 复用 TeacherOfflineList 风格
5. `CourseDetail.vue` 注释"4 选 1" → "5 类课件"
6. 横向扫描所有 `videoCourse` / `视频课程` 字面 → 跟随 getCourseTypeConfig

## 验证标准
- [ ] ADMIN/ACADEMIC/TEACHER 三角色登录后菜单都显示 5 种独立入口（HTML/PPT/视频/练习/线下）
- [ ] 5 种入口全部可点击进入对应管理页
- [ ] 学生端可见课件标签与用户用语一致（"视频课件"非"视频课程"）
- [ ] 后端 CourseType 枚举不变（V333 兼容 + 无 DB migration）
- [ ] precheck 26/26 通过 + eslint 通过

## 提交策略
- 单一 PR `fix/5-courseware-types-independent-management`
- 五段式根因分析 + 横向扫描 + 防止再发
- CI 5/5 + auto-approve + squash merge

## 不做项（避免范围爆炸）
- 不新增后端 CourseType 枚举值（V333 已部署 + DB 无 EXERCISE 课程维度数据）
- 不重构章节/课程层级关系（与用户指令无关）
- 不改学生端 LearningView 等章节级逻辑（仅显示标签跟随配置）