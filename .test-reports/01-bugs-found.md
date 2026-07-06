# Bug 记录表

> 实时累加。每发现一个 Bug 加一行。

## 格式说明

```
### BUG-NNN · [级别] · 模块名

- **复现步骤**：
- **预期结果**：
- **实际结果**：
- **证据**：
- **根因**（白盒）：
- **修复建议**：
```

---

## 统计

- P0（数据安全 / 不可用）：0
- P1-C（客户可见）：7+
- P1-I（内部）：1
- P2（建议）：1
- **总计**：9+

---

## 已测模块记录（即使没发现 Bug 也记录，留作回归基线）

### ✅ 认证模块（部分通过）

| 测试点 | 状态 | 备注 |
|---|---|---|
| 空字段登录 | ✅ 通过 | 必填提示正常 |
| 长度不足（6 字符） | ✅ 通过 | 提示"长度 8-32" |
| 纯字母无数字 | ✅ 通过 | 提示"需包含字母和数字" |
| 字母+数字但密码错 | ✅ 通过 | 提示"用户名或密码错误"（防枚举 ✅） |
| 正确密码登录 | ✅ 通过 | 跳转 dashboard |

### ✅ 课程管理-列表（部分通过）

| 测试点 | 状态 | 备注 |
|---|---|---|
| 列表加载 | ✅ 通过 | 9+ 课程正常显示 |
| 关键字搜索"互动" | ✅ 通过 | 准确过滤 |
| 导出数据 | ✅ 通过 | 导出 25 条，下载 xlsx |
| 路由 /admin/courses | ❌ 跳转首页 | 见 BUG-006 |
| 路由 /courses | ✅ 通过 | 正常 |
| 课程类型按钮显隐 | ✅ 通过 | "互动"有"课件"按钮，"视频"无 |

---

## Bug 列表

### BUG-001 · P1-C · 数据看板 - 核心指标趋势图始终不渲染

- **复现步骤**：
  1. 用 admin 登录
  2. 进入"数据看板" → "数据总览"
  3. 滚动到中部"核心指标趋势"卡片
- **预期结果**：显示 7 天内的用户/课程/学员增长折线图
- **实际结果**：卡片标题可见，但图表区域完全空白
- **证据**：
  - `/api/admin/stats/users?days=7` 返回 200（719 bytes）
  - `/api/admin/stats/courses?days=7` 返回 200（734 bytes）
  - DOM 中 `.chart-container` 存在，511x300，**`innerHTML === ""`**
- **根因**：`micro-course-admin/src/views/admin/Dashboard.vue:526-548` `loadTrends()` 流程错：
  ```js
  trendsLoading.value = true
  await Promise.all([...])
  renderTrendsChart(data)      // ❌ 此时 trendsChartRef.value === null
  trendsLoading.value = false
  ```
  `renderTrendsChart` 首行 `if (!trendsChartRef.value) return`，调用时 ref 还没挂载。
- **修复建议**：在 `renderTrendsChart` 之前 `await nextTick()`，并把 `trendsLoading.value = false` 移到 `nextTick` 之后。

---

### BUG-002 · P1-C · 数据看板 - 课程分类分布饼图始终不渲染

- **复现步骤**：同 BUG-001，改为"课程分类分布"卡片
- **预期结果**：显示 7 种状态课程数饼图
- **实际结果**：空白
- **证据**：`/api/admin/stats/course-distribution` 返回 200（589 bytes），DOM 容器 349x300 空
- **根因**：`Dashboard.vue:601-616` 与 BUG-001 同款问题。**同时**有 BUG-003。
- **修复建议**：与 BUG-001 相同 + 修 BUG-003。

---

### BUG-003 · P1-C · 数据看板 - 课程分类分布 数据结构不匹配

- **复现步骤**：即使 BUG-002 修复
- **预期结果**：图例显示"草稿/待审核/已通过/已驳回/已发布/已关闭/已归档"，数值正确
- **实际结果**：图例显示"0: [object Object]" "1: [object Object]" 之类乱码
- **证据**：
  - 后端 `course-distribution` 返回 `{data: [{status:"DRAFT", count:13}, ...]}` —— **数组**
  - 前端 `loadCategoryStats:606` `const data = res.data || {}` —— 假设是对象
  - 前端 `loadCategoryStats:607` `Object.entries(data).map(...)` —— 对数组用 `Object.entries`
- **根因**：`Dashboard.vue:601-616`
- **修复建议**：
  ```js
  const items = (res.data || []).map(item => ({
    name: item.status, value: item.count
  }))
  ```

---

### BUG-004 · P1-C · 数据看板 - 最近 30 天活跃趋势图始终不渲染

- **复现步骤**：同 BUG-001，改为"最近 30 天活跃"卡片
- **预期结果**：显示 30 天内每日活跃用户数折线图
- **实际结果**：空白
- **证据**：`/api/admin/stats/daily-activity?days=30` 返回 200（1502 bytes），DOM 511x300 空
- **根因**：`Dashboard.vue:645-659` 与 BUG-001 同款
- **修复建议**：与 BUG-001 相同

---

### BUG-005 · P2 · 数据看板 - 磁盘 WARN 状态缺少详情提示

- **复现步骤**：进入 Dashboard 滚到底部"系统健康"，hover "磁盘 WARN"
- **预期结果**：tooltip 显示磁盘详情（哪个盘/剩余/阈值）
- **实际结果**：无 tooltip
- **根因**：`Dashboard.vue:281-302` 渲染 `health` 时未传详情数据；后端 `/admin/stats/health` 也只返状态码
- **修复建议**：后端加 `diskUsagePercent` / `diskFreeGB` / `diskTotalGB` 字段；前端 `<el-tooltip>` 包裹 WARN 项

---

### BUG-006 · P1-C · 课程管理 - /admin/courses 路由不存在

- **复现步骤**：
  1. admin 登录
  2. 浏览器地址栏输入 `http://localhost:5173/admin/courses` 回车
- **预期结果**：进入课程列表
- **实际结果**：跳回首页，提示"页面 '/admin/courses' 不存在"
- **证据**：
  - 路由表 `micro-course-admin/src/router/index.js:22-25` 课程相关路由是 `/courses`、`/courses/:id`、`/courses/:id/edit`、**没有 `/admin/courses`**
  - 菜单点击"全部课程"能进，**说明菜单用的是 `name: 'CourseList'` 跳转**
  - 但**直接 URL 访问或被书签、邮件、第三方链接引导**时会 404
- **根因**：路由命名不一致 —— admin 后台用了 `/admin/*` 前缀（dashboard/users/logs），但课程相关是 `/courses` 顶级路由
- **修复建议**：添加 `/admin/courses` 重定向到 `/courses`，保持与 `/admin/dashboard` 等风格一致：
  ```js
  { path: '/admin/courses', redirect: '/courses' },
  { path: '/admin/courses/:id', redirect: '/courses/:id' },
  ```

---

### BUG-007 · P1-C · 课程管理 - 新增课程对话框在 720 高度视口下底部被裁切

- **复现步骤**：
  1. admin 登录
  2. 进入"全部课程"
  3. 点击"新增课程"按钮
- **预期结果**：弹出"新增课程"对话框，**底部"确定/取消"按钮可见**
- **实际结果**：对话框 905px 高，但视口只有 720px，**底部 293px（含"确定/取消"按钮和最后几个字段）被裁切**
- **证据**：
  - Dialog 元素 bounding rect: `{x:290, y:108, width:700, height:905, bottom:1013}`
  - 视口 height: 720
  - **底部"确定/取消"按钮和"封面"上传区不可见**
- **根因**：`CourseList.vue:149` `<el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">` **没设置 `teleport-to="body"`**，导致 dialog 被 main 容器的 `overflow: auto` 裁切
  ```html
  <!-- 现状 -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="handleDialogClose" :close-on-press-escape="true">
  <!-- 修复 -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" teleport-to="body" @close="handleDialogClose" :close-on-press-escape="true">
  ```
- **修复建议**：
  1. 给所有 el-dialog 加 `teleport-to="body"`（或者全项目加一个全局样式 `.el-dialog { margin-top: 5vh !important; max-height: 90vh; overflow-y: auto; }`）
  2. **影响范围**：全项目所有 dialog（至少要扫一遍 `micro-course-admin/src/views/**/*.vue`）

---

### BUG-008 · P1-I · 课程管理 - 课程封面列全部显示"—"

- **复现步骤**：进入"全部课程"列表
- **预期结果**：封面列显示真实图片（如果有）
- **实际结果**：所有 9+ 课程封面列都显示"—"
- **证据**：列表中所有"封面"列都是"—"
- **根因**（猜测）：封面是后端字段 `coverUrl` 但前端用了 `imageUrl` 或渲染时 url 为空时直接显示"—"占位（**未完全确认**，需查代码）
- **修复建议**：查 `CourseList.vue` 封面列模板渲染逻辑，确认字段名是否匹配；增加无封面占位图

---

## 同类问题横向扫描（已完成部分）

⚠️ **BUG-009（横向扫描结果）：nextTick 时机 Bug 至少影响 3 个 Dashboard**

通过 `grep -l "echarts"` 找出 4 个文件用 ECharts：
- ✅ `admin/Dashboard.vue` —— 3 个图全空白（BUG-001/002/004）
- 🐛 `teacher/TeacherDashboard.vue:597-613 loadActivity()` —— 同样错（`activityLoading=true → await → renderStudyChart+renderActiveChart → loading=false`），图表会空白
- 🐛 `academic/Dashboard.vue:462 loadDepartmentStats() + 549 loadTrend()` —— 同样错，部门饼图 + 参与趋势图会空白
- 🐛 `teacher/StudentGrades.vue:355-381` —— 同样错，renderChart 也会空白

**修复方案统一**（适用所有 Dashboard）：
```js
async function loadXxx() {
  loading.value = true
  try {
    const data = ...
    await nextTick()              // 等 chart 容器挂载
    loading.value = false
    await nextTick()              // 等 skeleton 卸载
    renderXxxChart(data)          // 此时 ref 一定有值
  } catch (e) { ... }
}
```

⚠️ **BUG-010（横向扫描结果）：teleport-to 缺失 Bug 影响 32 个文件**

`grep -L "teleport" $(grep -l "<el-dialog" -r .)` 找出 32 个 vue 文件有 `<el-dialog>` 但没 `teleport` 属性。这些 dialog 全部会被 main 容器 `overflow: auto` 裁切。

**修复方案统一**（2 选 1）：
1. **加 `teleport-to="body"` 到所有 `<el-dialog>`**（32 处，机械工作但最稳）
2. **加全局 CSS 样式**（1 处，治本但需测试）：
   ```css
   .el-dialog {
     position: fixed !important;
     max-height: 90vh;
     margin: 5vh auto !important;
   }
   .el-dialog__body {
     max-height: calc(90vh - 120px);
     overflow-y: auto;
   }
   ```

**推荐方案 1**（更稳，避免破坏其他定位逻辑）。需要修改的文件列表：
```
settings/PlatformShareConfig.vue, auth/Login.vue, classes/ClassList.vue,
student/Checkout.vue, student/CourseSquare.vue, student/CourseDetail.vue,
student/DiscussionView.vue, admin/TeachingClassList.vue, admin/TeacherRatingManage.vue,
admin/OperationLogs.vue, admin/UserList.vue, majors/MajorList.vue,
departments/DepartmentList.vue, academic/MicroSpecialtyFeaturedReview.vue,
academic/MicroSpecialtyProposalReview.vue, academic/StorageApplicationReview.vue,
academic/MicroSpecialtyClassImport.vue, academic/MicroSpecialtyReview.vue,
academic/MicroSpecialtyCrossDeptReview.vue, courses/BundleList.vue,
courses/ExerciseList.vue, courses/ChapterList.vue, courses/QuestionList.vue,
courses/TagList.vue, courses/QuestionPreview.vue, courses/ExerciseForm.vue,
courses/CourseDetail.vue, courses/CourseList.vue, courses/VideoList.vue,
courses/CourseCategoryList.vue, ...
```

---

## 测试进度

- 测了 2/25 个模块
- 用了 ~20 分钟
- 已发现 8 个 Bug（7 P1-C + 1 P2）
