# 微课平台 · 测试环境验证报告

> 生成时间: 2026-06-12 06:00 (UTC+8)
> 后端地址: http://localhost:8080

---

## 1. 测试用户凭证

| 用户 | 用户名 | 密码 | 角色 | UserID |
|------|--------|------|------|--------|
| 管理员 | admin | admin123 | ADMIN | 1 |
| 学生 | student | 123456 | STUDENT | 7 |
| 教师 | teacher | 123456 | TEACHER | 8 |

---

## 2. JWT Token 清单

```
ADMIN_TOKEN=eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImp0aSI6IjE3ZWQ2MTc5LTNiNmYtNGM0My1hM2Q3LWFiYjRlZDJmOWFkOCIsImlhdCI6MTc4MTIxNTA4NiwiZXhwIjoxNzgxMjIyMjg2fQ.woyXizCnd8TkQg09EXDePJ3XzQtAnZ5LJc7Ki0G6Xhq_7HSPSLIaXkl7nK2ZIU2T

STUDENT_TOKEN=eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiI3IiwidXNlcm5hbWUiOiJzdHVkZW50Iiwicm9sZSI6IlNUVURFTlQiLCJqdGkiOiIxY2E3MDQxYy0zODI0LTRjMzItOGZiZC1kZDEyZjVkNjkxM2QiLCJpYXQiOjE3ODEyMTUwODYsImV4cCI6MTc4MTIyMjI4Nn0.Vwl7dQK6DnEwpGg7G9PE3gR7nhcUwwK6pJOnKFozMdBfAPv31CiiAFAiebIT4hHt

TEACHER_TOKEN=eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiI4IiwidXNlcm5hbWUiOiJ0ZWFjaGVyIiwicm9sZSI6IlRFQUNIRVIiLCJkZXBhcnRtZW50SWQiOjMsImp0aSI6IjQ4OGM4MmIyLTFhZWQtNDJmNi05NjVlLTVjOTM1YzI3YmJiOSIsImlhdCI6MTc4MTIxNTA4NiwiZXhwIjoxNzgxMjIyMjg2fQ.RCGLJBU7s7EDL2U0MeuPzQ7_RtBKgUcvlAP6YneXrs9gSQdIrdAaBwJSG7DH5dWZ
```

---

## 3. API 数据状态

| API | 数据量 | 状态 |
|-----|--------|------|
| GET /api/departments | 5 条 | ✅ 正常 |
| GET /api/majors | 5 条 | ✅ 正常 |
| GET /api/classes | 5 条 | ✅ 正常 |
| GET /api/users | 5 条 | ✅ 正常 |
| GET /api/courses | 5 条 | ✅ 正常 |
| GET /api/chapters | 5 条 | ⚠️ 有 XSS 注入数据 |
| GET /api/videos?courseId=4 | 1 条 | ✅ 正常 |
| GET /api/questions | 5 条 | ✅ 正常 |
| GET /api/categories | - | ❌ 500 错误 |

---

## 4. 已创建测试数据

### 用户
- student (ID:7, STUDENT)
- teacher (ID:8, TEACHER)

### 课程相关
- 课程: Java编程基础 (ID:4, 状态: 草稿)
- 章节: 第1章 Java入门 (ID:15)
- 章节: 第2章 变量和数据类型 (ID:16)
- 视频: Java环境搭建视频 (ID:2, 关联章节ID:15)
- 题目: JDK环境验证 (ID:2)
- 题目: Java入口方法 (ID:3)

---

## 5.已知问题

1. **XSS 注入风险**: chapters 表中存在 `<script>alert(1)</script>` 标题
2. **categories API 500**: GET /api/categories 返回服务器内部错误
3. **exercises API 字段要求**: exercises 创建时需要关联题目，但 API 字段验证有问题

---

## 6. 使用示例

```bash
# 调用需认证 API
curl -H "Authorization: Bearer $STUDENT_TOKEN" \
     http://localhost:8080/api/courses

# 学生登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student","password":"123456"}'
```