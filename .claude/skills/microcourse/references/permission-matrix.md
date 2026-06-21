# 权限矩阵引用视图

> **源文档**：[`docs/权限矩阵.md`](../../权限矩阵.md) v2.0
> **视图性质**：引用视图
> **冲突裁决**：以冲突评审决议为准（v2.0 已落地 C1+C4）

---

## 1. 4 角色 × 资源矩阵

| 角色 | 代码 | 范围 |
|------|------|------|
| 学生 | STUDENT | 看课/选课/学习 |
| 教师 | TEACHER | 看本院系/本班学生/授课 |
| 管理员 | ADMIN | 全权（CRUD 所有资源） |
| 教务处 | ACADEMIC | 数据驾驶舱/统计/审核 |

---

## 2. Phase 1 18 个 API 的权限注解

### 2.1 用户认证（5 个）

| API | 方法 | 路径 | 权限 |
|-----|------|------|------|
| 登录 | POST | `/api/auth/login` | 公开 |
| 刷新 Token | POST | `/api/auth/refresh` | 已认证 |
| 登出 | POST | `/api/auth/logout` | 已认证 |
| CAS 回调 | GET | `/api/auth/cas` | 公开 |
| 当前用户 | GET | `/api/auth/me` | 已认证 |

### 2.2 院系管理（5 个）

| API | 方法 | 路径 | 权限 |
|-----|------|------|------|
| 列表 | GET | `/api/departments` | 已认证 |
| 创建 | POST | `/api/departments` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 详情 | GET | `/api/departments/{id}` | 已认证 |
| 更新 | PUT | `/api/departments/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 删除 | DELETE | `/api/departments/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |

### 2.3 专业管理（5 个）

| API | 方法 | 路径 | 权限 |
|-----|------|------|------|
| 列表 | GET | `/api/majors` | 已认证 |
| 创建 | POST | `/api/majors` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 详情 | GET | `/api/majors/{id}` | 已认证 |
| 更新 | PUT | `/api/majors/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 删除 | DELETE | `/api/majors/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |

### 2.4 班级管理（5 个）

| API | 方法 | 路径 | 权限 |
|-----|------|------|------|
| 列表 | GET | `/api/classes` | 已认证 |
| 创建 | POST | `/api/classes` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 详情 | GET | `/api/classes/{id}` | 已认证 |
| 更新 | PUT | `/api/classes/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 删除 | DELETE | `/api/classes/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |

### 2.5 用户管理（5+1 个）

| API | 方法 | 路径 | 权限 |
|-----|------|------|------|
| 列表 | GET | `/api/users` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |
| 创建 | POST | `/api/users` | `@PreAuthorize("hasRole('ADMIN')")` |
| 详情 | GET | `/api/users/{id}` | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC') or hasRole('TEACHER') or #id == authentication.principal.id")` |
| 更新 | PUT | `/api/users/{id}` | `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")` |
| 状态 | **PUT** | **`/api/users/{id}/status`** | `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` |

---

## 3. SecurityConfig 路径配置

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/login", "/api/auth/cas", "/api/auth/refresh").permitAll()
    .requestMatchers("GET", "/api/admin/stats/health").permitAll()
    .requestMatchers("GET", "/api/enums/export").permitAll()
    .requestMatchers("GET", "/api/system-configs/public").permitAll()
    .requestMatchers("GET", "/api/files/covers/**", "/api/files/avatars/**",
                     "/api/files/banners/**", "/api/files/system/**").permitAll()
    .requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
    .requestMatchers("GET", "/api/departments/**", "/api/majors/**", "/api/classes/**").authenticated()
    .requestMatchers("/api/auth/**", "/api/users/**").authenticated()
    .requestMatchers("GET", "/api/videos/stream/**").authenticated()
    .requestMatchers("GET", "/api/files/**").authenticated()
    .requestMatchers("/api/**").permitAll() // 未知路由→Dispatcher→GlobalExceptionHandler→404
    .anyRequest().authenticated()
);
```

**两层防御**：
- SecurityConfig 路径配置 = 粗粒度（仅认证/公开，角色校验全部委托 @PreAuthorize）
- @PreAuthorize 注解 = 细粒度（资源/行级角色控制）

**说明**：@PreAuthorize（配合 @EnableMethodSecurity）是角色鉴权的主防线，SecurityConfig 仅做路径级认证控制。未知 API 路径通过 `/api/**` permitAll 放行给 Dispatcher 返回 404。

---

## 4. AI 编码陷阱

```
❌ Controller 忘记加 @PreAuthorize
❌ 用 /api/v1/... 路径
❌ 把 /me 放到 /api/users/me
❌ DELETE /api/users/{id} 写软删
✅ 每个 Controller 方法都加 @PreAuthorize
✅ 路径前缀 /api/
✅ /me 在 /api/auth/me
✅ 软删走 PUT /api/users/{id}/status
```

---

*视图版本：v1.0 · 与源文档 v2.0 对齐*
*最后更新：2026-06-11*
