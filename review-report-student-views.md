# 静态代码审查报告 — 学生端 3 个 Vue 文件

审查范围（只读，未修改任何文件）：
- `micro-course-admin/src/views/student/Profile.vue`
- `micro-course-admin/src/views/student/Settings.vue`
- `micro-course-admin/src/views/student/MyReviews.vue`

已交叉核实：`src/api/auth.js`、`src/api/review.js`、`src/api/course-review.js`、`src/api/notification-preference.js`、`src/store/user.js`、`src/utils/fetchAllPages.js`、`src/main.js`（`$formatDateTime` 全局注册于 main.js:46 ✓）、`src/i18n/zh-CN.js`（命名空间 myReviews/studentSettings/user/course/common/app/layout 均存在 ✓）、5 个 profile 子组件的 `defineProps`（均声明 `isMobile` ✓）。

---

## P0/P1 级别

### 1. [Settings.vue:202, 351] 模板引用未声明的变量 `saveTimer`，保存按钮 loading 恒为 false（防重复提交形同虚设）
```html
<el-button type="primary" :loading="!!saveTimer" @click="handleSave" ...>{{ $t('studentSettings.saveSettings') }}</el-button>
```
script 中只有 `let debounceTimer = null`（Settings.vue:495），**全文件没有任何 `saveTimer` 声明与赋值**（grep 确认仅出现于 202/351 两处模板）。
→ 触发条件：每次渲染该按钮时（页面加载完成、非 loading/error 状态下，PC 与 H5 两个布局都命中）。
→ 业务影响：Vue dev 每次渲染控制台报 `Property "saveTimer" was accessed during render but is not defined on instance`（编译产物以 `_ctx.saveTimer` 访问 → undefined → false，不白屏但永远不生效）；保存按钮永远不进入 loading/禁用态，保存期间无任何视觉反馈，重复点击无按钮级拦截（仅靠 300ms 防抖兜底）。网络慢时用户会反复点击。
→ 根因分析：作者本意用 `saveTimer` 标记防抖保存进行中（注释 P1I-031），但既未声明该变量，也未在 `debouncedSave` 中维护；`debouncedSave` 实际只使用 `debounceTimer`。修复应声明 `const saveTimer = ref(false)`，在 `debouncedSave` 的 setTimeout 回调内 await 前后置 true/false。

### 2. [Settings.vue:114-137（PC）/ 282-305（H5），394-408] 免打扰时段时间选择器：已保存值不回显 + v-model 与 :value 双绑冲突 + Date/string 类型混用
```html
<el-time-picker
  v-model="quietHoursStartDate"          <!-- ref(new Date())，带 value-format="HH:mm" -->
  :value="settings.quietHoursStart"      <!-- "22:00" 字符串，el-time-picker 无 value prop → 透传死绑定 -->
  format="HH:mm" value-format="HH:mm" ... @change="onQuietHoursStartChange" />
```
script：`const quietHoursStartDate = ref(new Date())`（:394），真实设置值在 `settings.quietHoursStart = '22:00'`（:386）。
→ 触发条件：`quietHoursEnabled` 为 true 时（PC/H5 均存在），每次重新进入页面且从未手动改过时间——即**每次刷新页面都必现**。
→ 业务影响（数据回显错误，检查清单第 11 项）：
  1. 选择器显示的是 `new Date()` 的**当前时刻**，而不是已保存的 22:00/07:00——用户看到的时间与真实设置不符；
  2. `:value="settings.quietHoursStart"` 不是 el-time-picker 的合法 prop（其模型 prop 是 `modelValue`），`value` 会被当作 fallthrough 属性透传，是死绑定，与 v-model 双写互相打架；
  3. 类型翻转：首次渲染 modelValue 是 Date，用户一旦改动（value-format="HH:mm"）`update:modelValue` 又写回 "HH:mm" 字符串，同一 ref 在 Date 与 string 之间反复横跳。
→ 根因分析：为防抖保存场景复制了两个 state（Date 显示态 + string 设置态）却没有同步通道——既没有从 `settings.quietHoursStart` 初始化 `quietHoursStartDate`，也没有 watch 同步。修复应让 v-model 直接绑 `settings.quietHoursStart`（字符串 + value-format），删除 `:value` 与两个 Date ref。

### 3. [Profile.vue:24 与 209] 移动端首屏同时渲染**两套骨架屏**，且桌面骨架以 PC 布局在 375px 下挤成两列
```html
<template v-else-if="!userStore.userInfo">   <!-- :24 桌面骨架：el-row + el-col :span="16"/:span="8"，未按 !isMobile 门控 -->
...
<template v-if="!userStore.userInfo && isMobile">  <!-- :209 移动骨架：与 :24 是并列的独立 v-if，不在 v-if/v-else-if 链内 -->
```
→ 触发条件：移动端（<768px）首次进入、`userStore.userInfo` 尚未加载完成（getInfo 在途）——**每次移动端首屏必现**；若 getInfo 失败（onMounted catch 将 userInfo 置 null，Profile.vue:389），错误结果（:11）之下还会再叠一套移动骨架。
→ 业务影响：移动端首屏出现桌面两列骨架（el-col 16/8 各占 66%/33%，卡片被挤成窄条）+ 移动骨架列表双重堆叠，加载态 UI 错乱；桌面骨架的 el-row/el-col 固定 span 布局在 375px 下明显不适配。
→ 根因分析：:24 分支缺少 `&& !isMobile` 门控；:209 骨架块本应替代 :24 分支（应并入 v-else-if 链），却写成独立 v-if，条件重叠时双渲染。

### 4. [MyReviews.vue:306] 删除确认框的取消判断只认 'cancel'，漏掉 'close' → 关闭弹窗误报删除失败
```js
} catch (err) {
  if (err === 'cancel') return
  ElMessage.error(t('myReviews.deleteFailed'))
}
```
→ 触发条件：点击确认框右上角 X 或按 Esc 关闭（Element Plus 拒绝原因同为字符串 'close'，而 `err === 'cancel'` 只匹配点取消按钮）。
→ 业务影响：用户只是关闭弹窗，却弹出红色删除失败错误提示，制造假性故障。
→ 根因分析：ElMessageBox 的 reject 值有 'cancel' 与 'close' 两种，判断条件遗漏后者；应改为 `if (err === 'cancel' || err === 'close') return`。

### 5. [MyReviews.vue:86-96] PC 端加载失败时暂无数据空态与加载失败错误态**同时**渲染
```html
<div v-if="reviews.length === 0 && !loading" class="empty-wrap">  <!-- :86 -->
  <el-empty :description="$t('myReviews.noData')" />
</div>
<div v-if="errorState" class="error-wrap">                        <!-- :90 -->
  <el-result icon="error" ...>
```
→ 触发条件：`fetchMyReviews` 抛错置 `errorState=true`（:287），此时 reviews 为空、loading=false → :86 与 :90 同时命中；而表格本体（:46 的 v-else 分支）也照常渲染一张空表。
→ 业务影响：PC 端失败态一屏三件套（空表格 + 暂无数据 + 错误结果卡片），信息互相矛盾；对比 H5 分支（:173 用 `v-else-if="!errorState"` 正确规避）PC 端处理不一致。
→ 根因分析：空态判断未排除 errorState，应改为 `v-if="reviews.length === 0 && !loading && !errorState"`。

---

## P2 / 体验

- [MyReviews.vue:207] `import { getReviews } from '@/api/course-review'` 全文件未使用（grep 仅 1 处出现），死 import。
- [MyReviews.vue:262, 277] `allReviews` 只在 :277 赋值、从未被读取（过滤用的是局部 `items`），死存储。
- [MyReviews.vue:322-324] `handleSizeChange` 不重置 `pagination.page`：用户在深页码（如第 5 页）切换 pageSize 后，`(page-1)*size` 起点越界，slice 为空 → 显示暂无数据而实际有数据；删除末页最后一条后（:304 直接 refetch）同理。应在 size 变化时 `page = 1`，并在过滤后对 page 做 clamp。
- [MyReviews.vue:244-259] `fetchCourseOptions` 只取 `getMyReviews({ page: 0, size: 100 })` 的前 100 条派生课程筛选项，评价 >100 条的用户筛选项不全（且未用 fetchAllPages，与主列表逻辑不一致）。
- [MyReviews.vue:218 vs 234] `isMobile` 初始化用 `window.innerWidth <= 768`，resize 回调用 `< 768`，断点不一致（正好 768px 时初始按移动、缩放后按 PC，行为跳变）。
- [MyReviews.vue:274] 注释"后端 getMyReviews(userId, page, size)"与实际 API 签名（`getMyReviews(params)` 对象参数，course-review.js:32）不符，误导后续维护。
- [Settings.vue:519, 526-528] `debouncedSave` 中先 `ElMessage.success(savedSuccess)`，随后 localStorage.setItem 抛 QuotaExceededError 时又 `ElMessage.error(saveFailed)`，同一操作连弹成功+失败两条 toast。
- [Profile.vue:77, 155] 模板 `ref="avatarUploadCompRef" / ref="avatarUploadMobileRef"` 未在 script 中声明对应 ref，属死引用（无功能影响，删除或补声明）。

---

## 该组无问题文件清单

无。3 个文件均有确凿发现（Profile 1 项 P1-C + 1 项 P2；Settings 2 项 P1-C + 1 项 P2；MyReviews 2 项 P1-C + 5 项 P2）。

---

## 检查清单 16 项逐项结论（已核对，未单独列出的项均为通过）
1. 模板引用未定义变量：❌ Settings `saveTimer`（见 P1-1）；Profile/MyReviews 模板引用均已定义 ✓
2. vue 组合式 API import：✓ 三文件均完整 import 所需 API（onMounted/onUnmounted/onBeforeUnmount/ref/nextTick/h/defineAsyncComponent 等）
3. 实例/模板全局：✓ `$formatDateTime` 已在 main.js:46 全局注册；`$t` 走 vue-i18n；script 中均用 `useI18n()` 的 t，无 this 引用
4. api import 核实：✓ uploadAvatar(auth.js:9)、getMyReviews(course-review.js:32)、deleteReview(review.js:24)、getMyPreferences/updateMyPreferences(notification-preference.js:3/7)、fetchAllPages(fetchAllPages.js:15，调用签名 `(getMyReviews, {}, 100)` 匹配) 全部真实存在；仅 getReviews 存在但未使用（P2）
5. 分页：fetchAllPages 内部 0 基循环 ✓；客户端切片 1 基 page ✓；但 pageSize 切换/删除后页码不 clamp（P2）
6. 单位：未发现 progress/时间/字节混用；avatarMaxSize=2MB 字节换算正确 ✓
7. 状态流转：本组文件无订单/退款/审核流转；删除评价流程 confirm → deleteReview → 重新拉取，顺序正确 ✓
8. @click handler：均指向已定义函数 ✓；无冒泡/disabled 组合问题
9. 空壳按钮/死 tab：无；删除按钮真实调用 deleteReview API（含注释声明）✓
10. 确认弹窗：删除有 ElMessageBox.confirm ✓；保存按钮防重因 saveTimer 未声明而失效（P1-1）
11. 数据回显：免打扰时段选择器回显错误（P1-2）；未发现 watch 双向回声死循环
12. 空态/加载态：三文件均有骨架/空态/错误态；但 PC 端错误态与空态叠加（P1-5）、移动端双骨架（P1-3）
13. 375px 适配：MyReviews H5 用 max-content chips + 横向滚动 ✓、Settings H5 自适应 ✓；Profile 桌面骨架漏渲染到移动端（P1-3）
14. 条件逻辑：err==='cancel' 遗漏 'close'（P1-4）；其余 !==/空判断正常
15. 模板 ref 解包：未发现模板内 .value 误用；el-time-picker 的 :value 非合法 prop 属死绑定（P1-2）
16. props/emits：Profile 传 `:is-mobile` 的 5 个子组件（UserInfoEditor/PasswordEditor/AchievementBadges/WrongQuestionsCard/CertificatesCard）defineProps 均声明 `isMobile: Boolean` ✓ 一致