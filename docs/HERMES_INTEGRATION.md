# Hermes → 微课平台 Webhook API 集成指南

> 版本：v1.1 · 2026-07-09
> 本指南供 Hermes 平台开发团队参考，实现与微课平台的课程双向同步。

---

## 1. 概述

微课平台提供 Webhook API，支持 Hermes 平台将课程推送到微课平台。

### 认证方式

每个教师在微课平台「个人设置」中生成自己的 **API Key**（64 字符随机字符串）。调用 API 时在 HTTP Header 中传入：

```
X-API-Key: <教师的 API Key>
```

### 基础 URL

```
生产环境: https://microcourse.ailyedu.cn/api/hermes/webhook
测试环境: http://localhost:8089/api/hermes/webhook
```

---

## 2. API 端点

### 2.1 创建/更新课程

将 Hermes 平台的课程推送（同步）到微课平台。**同一 `hermesCourseId` 重复调用时自动覆盖更新（upsert）。**

```
POST /api/hermes/webhook/courses
Content-Type: application/json
X-API-Key: <教师 API Key>
```

#### 请求体

```json
{
  "hermesCourseId": "HM-2026-001",
  "title": "Python 入门",
  "subtitle": "零基础也可以学",
  "summary": "本课程面向编程初学者...",
  "coverUrl": "https://cdn.hermes.com/covers/001.jpg",
  "categoryId": 1,
  "tags": "Python,入门,编程",
  "courseType": "NORMAL",
  "difficulty": 1,
  "maxStudents": 100,
  "semester": "2026-2027-1",
  "creditHours": 4.0,
  "courseNature": "公共选修",
  "offerDepartmentId": 1,
  "chapters": [
    {
      "title": "第一章 基础",
      "sortOrder": 1,
      "lessons": [
        {
          "title": "1.1 什么是Python",
          "type": "VIDEO",
          "contentUrl": "https://cdn.hermes.com/videos/001.mp4",
          "durationMinutes": 15,
          "sortOrder": 1
        }
      ]
    }
  ],
  "pricing": {
    "isFree": false,
    "price": 199.00,
    "freeAccessScope": "none",
    "freeDeptIds": "[]"
  }
}
```

#### 字段说明

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `hermesCourseId` | ✅ | string | Hermes 侧课程唯一 ID，同 ID 重复调用会覆盖更新 |
| `title` | ✅ | string | 课程标题 |
| `categoryId` | ✅ | number | 微课平台分类 ID（见下方分类列表） |
| `teacherId` | ❌ | number | 微课平台教师用户 ID。**传了必须等于 X-API-Key 对应的教师；不传则默认为 API Key 所属教师** |
| `subtitle` | ❌ | string | 副标题 |
| `summary` | ❌ | string | 简介（≤300 字） |
| `coverUrl` | ❌ | string | 封面图片 URL |
| `tags` | ❌ | string | 标签（逗号分隔） |
| `courseType` | ❌ | string | `NORMAL` / `VIDEO` / `INTERACTIVE` / `OFFLINE`，默认 `NORMAL` |
| `difficulty` | ❌ | number | 难度 1-5 |
| `maxStudents` | ❌ | number | 最大选课人数 |
| `semester` | ❌ | string | 学期 |
| `creditHours` | ❌ | number | 学分 |
| `courseNature` | ❌ | string | 课程性质 |
| `offerDepartmentId` | ❌ | number | 开课院系 ID |
| `description` | ❌ | string | 详情描述（HTML，自动 XSS 过滤） |
| `chapters` | ❌ | array | 章节和课时的树形结构 |
| `pricing` | ❌ | object | 定价信息 |

#### 章节/课时结构

```
chapters: [
  {
    title: "第一章",        // 章节标题
    sortOrder: 1,           // 排序
    lessons: [
      {
        title: "1.1 第一节",    // 课时标题
        type: "VIDEO",          // VIDEO / INTERACTIVE / OFFLINE / EXERCISE
        contentUrl: "...",      // 视频 URL
        durationMinutes: 15,    // 时长（分钟）
        sortOrder: 1            // 排序
      }
    ]
  }
]
```

#### 定价结构

```json
pricing: {
  "isFree": false,            // 是否免费
  "price": 199.00,            // 价格（付费时必填）
  "freeAccessScope": "none",  // 免费范围: none / same_department / same_college / same_school
  "freeDeptIds": "[]"         // 免费院系 ID 列表 JSON
}
```

#### 成功响应

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "courseId": 42,
    "status": "DRAFT",
    "action": "created"
  }
}
```

`action` 取值：
- `created` — 首次同步，已创建新课程
- `updated` — 已覆盖更新现有课程

课程初始状态为 **DRAFT（草稿）**，教师登录后可在微课平台继续编辑、提交审核。

---

### 2.2 获取教师课程列表

```
GET /api/hermes/webhook/courses
X-API-Key: <教师 API Key>
```

#### 成功响应

```json
{
  "code": 200,
  "message": "ok",
  "data": [
    {
      "hermesCourseId": "HM-2026-001",
      "courseId": 42,
      "title": "Python 入门",
      "status": 0,
      "statusText": "DRAFT",
      "categoryId": 1,
      "categoryName": "编程基础",
      "courseType": "NORMAL",
      "lastSyncAt": "2026-07-09T10:00:00",
      "createdAt": "2026-07-09T10:00:00"
    }
  ]
}
```

---

### 2.3 获取单门课程详情

```
GET /api/hermes/webhook/courses/{hermesCourseId}
X-API-Key: <教师 API Key>
```

#### 成功响应

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "hermesCourseId": "HM-2026-001",
    "courseId": 42,
    "title": "Python 入门",
    "subtitle": "零基础也可以学",
    "summary": "本课程面向编程初学者...",
    "coverUrl": "https://cdn.hermes.com/covers/001.jpg",
    "categoryId": 1,
    "categoryName": "编程基础",
    "teacherId": 22,
    "teacherName": "张老师",
    "offerDepartmentId": 1,
    "semester": "2026-2027-1",
    "status": 0,
    "statusText": "DRAFT",
    "courseType": "NORMAL",
    "tags": "Python,入门,编程",
    "pricing": {
      "isFree": false,
      "price": 199.00,
      "freeAccessScope": "none",
      "freeDeptIds": "[]"
    },
    "chapters": [
      {
        "id": 5,
        "title": "第一章 基础",
        "sortOrder": 1,
        "lessons": [
          {
            "id": 12,
            "title": "1.1 什么是Python",
            "lessonType": "VIDEO",
            "durationMinutes": 15,
            "sortOrder": 1
          }
        ]
      }
    ],
    "lastSyncAt": "2026-07-09T10:00:00",
    "createdAt": "2026-07-09T10:00:00"
  }
}
```

---

## 3. 错误码

| HTTP | code | message | 说明 |
|------|------|---------|------|
| 401 | 21001 | 无效的 Hermes API Key | X-API-Key 缺失、错误或已撤销 |
| 400 | 9005 | 参数错误 | 请求体验证失败，具体见 message |
| 403 | 10003 | 无权限 | API Key 身份与 body 中的 teacherId 不一致 |
| 404 | 6001 | 课程不存在 | 查询的 hermesCourseId 未找到 |
| 500 | — | 服务器内部错误 | 请联系管理员 |

---

## 4. 集成流程

### 4.1 教师注册 API Key

1. 教师登录微课平台 → 点击菜单「个人设置 > 个人资料 / API Key」
2. 点击「生成 API Key」→ 复制生成的 64 字符密钥
3. 将 API Key 配置到 Hermes 平台，关联到该教师账号

> ⚠️ **安全提示**：API Key 只在生成时显示一次，关闭后无法再次查看。如遗失可重新生成，旧 Key 立即失效。

### 4.2 Hermes 推送课程

```
POST https://microcourse.ailyedu.cn/api/hermes/webhook/courses
X-API-Key: 377fc42aee7b4c05bc4dd796877c21511dc8ef0fab2f43cf8ddc28a686e10be1
Content-Type: application/json

{
  "hermesCourseId": "HM-001",
  "title": "...",
  "categoryId": 1,
  "chapters": [...],
  "pricing": {...}
}
```

### 4.3 Hermes 拉取课程列表

```
GET https://microcourse.ailyedu.cn/api/hermes/webhook/courses
X-API-Key: 377fc42aee7b4c05bc4dd796877c21511dc8ef0fab2f43cf8ddc28a686e10be1
```

### 4.4 同步说明

- **推送（Push）**：Hermes 编辑课程后 → 调用 POST → 微课平台创建/更新课程
- **拉取（Pull）**：Hermes 需要同步状态时 → 调用 GET 列表/详情 → 获取课程当前数据

---

## 5. cURL 示例

```bash
# 推送课程
curl -X POST https://microcourse.ailyedu.cn/api/hermes/webhook/courses \
  -H "Content-Type: application/json" \
  -H "X-API-Key: 377fc42aee7b4c05bc4dd796877c21511dc8ef0fab2f43cf8ddc28a686e10be1" \
  -d '{
    "hermesCourseId": "HM-001",
    "title": "Python 入门",
    "categoryId": 1,
    "chapters": [
      {
        "title": "第一章",
        "sortOrder": 1,
        "lessons": [
          {"title": "第一节", "type": "VIDEO", "durationMinutes": 15, "sortOrder": 1}
        ]
      }
    ],
    "pricing": {"isFree": true}
  }'

# 拉取课程列表
curl -H "X-API-Key: 377fc42aee7b4c05bc4dd796877c21511dc8ef0fab2f43cf8ddc28a686e10be1" \
  https://microcourse.ailyedu.cn/api/hermes/webhook/courses

# 拉取单门课程
curl -H "X-API-Key: 377fc42aee7b4c05bc4dd796877c21511dc8ef0fab2f43cf8ddc28a686e10be1" \
  https://microcourse.ailyedu.cn/api/hermes/webhook/courses/HM-001
```

---

## 6. 分类 ID 查询

> 以下为常用分类，完整列表可通过 `GET https://microcourse.ailyedu.cn/api/course-categories` 获取。

| ID | 名称 |
|----|------|
| 1 | 编程基础 |
| 2 | 数据结构 |
| 3 | 算法设计 |
| … | … |

---

## 7. 注意事项

1. **幂等性**：同一 `hermesCourseId` 重复 POST 不会创建重复课程，而是覆盖更新已有课程
2. **初始状态**：通过 Webhook 创建的课程默认 **DRAFT（草稿）**，需教师登录后提交审核
3. **教师身份**：API Key 代表教师身份，创建课程的 `teacherId` 自动绑定为该教师
4. **数据安全**：`description` 字段会自动做 XSS 过滤
5. **课程定价**：定价状态独立于课程状态，创建时设置的定价默认为 DRAFT，需提交定价审核
6. **错误处理**：所有错误返回标准格式 `{"code": XXX, "message": "..."}`

---

## 8. 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.1 | 2026-07-09 | 新增 GET 列表/详情端点、每教师独立 API Key 认证 |
| v1.0 | 2026-07-08 | 初始版本，POST 创建/更新课程 |
