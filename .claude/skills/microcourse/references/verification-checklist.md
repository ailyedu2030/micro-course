# 验证清单引用视图

> **源文档**：Phase 1 验证清单（独立 9 节 + §10 性能指标预留）
> **视图性质**：引用视图（精简为可勾选项）
> **使用方式**：每个工单交付前**逐项打勾**，未通过 = 工单未交付

---

## 1. 文件位置检查（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 1.1 | 后端 Entity 位于 `micro-course-api/src/main/java/com/microcourse/entity/` | |
| 1.2 | 后端 Controller 位于 `micro-course-api/src/main/java/com/microcourse/controller/` | |
| 1.3 | 后端 Service 位于 `micro-course-api/src/main/java/com/microcourse/service/` | |
| 1.4 | 后端 ServiceImpl 位于 `micro-course-api/src/main/java/com/microcourse/service/impl/` | |
| 1.5 | 后端 Mapper 位于 `micro-course-api/src/main/java/com/microcourse/repository/` | |
| 1.6 | 后端 DTO 位于 `micro-course-api/src/main/java/com/microcourse/dto/` | |
| 1.7 | 后端 Enum 位于 `micro-course-api/src/main/java/com/microcourse/enums/` | |
| 1.8 | 后端 Exception 位于 `micro-course-api/src/main/java/com/microcourse/exception/` | |
| 1.9 | 启动类位于 `micro-course-api/src/main/java/com/microcourse/MicroCourseApplication.java` | |
| 1.10 | 前端 Vue 页面位于 `micro-course-admin/src/views/` | |
| 1.11 | 前端 API 封装位于 `micro-course-admin/src/api/` | |
| 1.12 | 前端组件位于 `micro-course-admin/src/components/` | |
| 1.13 | 前端路由位于 `micro-course-admin/src/router/` | |
| 1.14 | 前端 Store 位于 `micro-course-admin/src/store/` | |

## 2. 重复定义检查（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 2.1 | 同名 Entity 不存在 | |
| 2.2 | 同名 Controller 不存在 | |
| 2.3 | 同名 Service 不存在 | |
| 2.4 | 同名 Mapper 不存在 | |
| 2.5 | 同名 Vue 组件不存在 | |
| 2.6 | 同名 API 方法不存在 | |

## 3. 字段类型一致性（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 3.1 | users 表 25 字段 DB/Java/TS 类型一致 | |
| 3.2 | departments 表 7 字段类型一致 | |
| 3.3 | majors 表 7 字段类型一致 | |
| 3.4 | classes 表 8 字段类型一致 | |

## 4. 枚举值一致性（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 4.1 | users.status 4 个值（INACTIVE/ACTIVE/DISABLED/DELETED） | |
| 4.2 | gender 2 个值（MALE/FEMALE） | |
| 4.3 | role 4 个值（STUDENT/TEACHER/ADMIN/ACADEMIC） | |

## 5. API 响应格式一致性（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 5.1 | 响应包含 `code: 200`（成功） | |
| 5.2 | 响应包含 `message: "ok"` | |
| 5.3 | 响应包含 `data: {...}` | |
| 5.4 | 响应包含 `timestamp: 1749620400000` | |
| 5.5 | 错误响应格式与成功响应一致 | |
| 5.6 | 分页响应 `items: [...]` | |
| 5.7 | 分页响应 `page: 0` | |
| 5.8 | 分页响应 `size: 20` | |
| 5.9 | 分页响应 `totalElements: 100` | |
| 5.10 | 分页响应 `totalPages: 5` | |

## 6. 权限注解检查（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 6.1 | 5 个 auth 端点权限注解正确 | |
| 6.2 | 院系 5 个端点权限注解正确 | |
| 6.3 | 专业 5 个端点权限注解正确 | |
| 6.4 | 班级 5 个端点权限注解正确 | |
| 6.5 | 用户 6 个端点权限注解正确（含 /status） | |
| 6.6 | SecurityConfig 路径配置覆盖所有端点 | |

## 7. 命名规范检查（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 7.1 | Java 类名 PascalCase | |
| 7.2 | Java 方法名 camelCase | |
| 7.3 | Java 字段名 camelCase | |
| 7.4 | DB 字段名 snake_case | |
| 7.5 | 表名无 sys_ 前缀 | |
| 7.6 | Vue 组件文件名 PascalCase.vue | |
| 7.7 | 路由路径 kebab-case | |
| 7.8 | API 方法小写 + 动词 | |
| 7.9 | REST 路径前缀 `/api`（无 /v1） | |

## 8. 业务逻辑检查（必过）

| # | 检查项 | ✅/❌ |
|---|--------|------|
| 8.1 | 院系删除前检查 majors 关联（错误码 2002） | |
| 8.2 | 专业删除前检查 classes 关联（错误码 3002） | |
| 8.3 | 班级删除前检查 users 关联（错误码 409） | |
| 8.4 | 用户禁用时设置 deleted_at | |
| 8.5 | 用户启用时清除 deleted_at | |
| 8.6 | 登录失败 5 次锁定 30 分钟（Redis） | |
| 8.7 | JWT claims 含 sub/username/role/departmentId | |
| 8.8 | accessToken 2h 过期 | |
| 8.9 | refreshToken 7d 过期 | |
| 8.10 | 用户禁用后 Token 立即失效 | |
| 8.11 | 列表 API 脱敏（realName/email/phone） | |
| 8.12 | /auth/me 不脱敏 | |

## 9. 总结

| 类别 | 通过项 | 失败项 | 通过率 |
|------|--------|--------|--------|
| 文件位置检查 | /14 | | |
| 重复定义检查 | /6 | | |
| 字段类型一致性 | /4 | | |
| 枚举值一致性 | /3 | | |
| API 响应格式一致性 | /10 | | |
| 权限注解检查 | /6 | | |
| 命名规范检查 | /9 | | |
| 业务逻辑检查 | /12 | | |
| **总计** | /64 | | |

**审查结论**：✅ 通过 / ❌ 不通过

---

## 10. 性能指标（§10 · 预留）

| # | 指标 | 目标值 | Phase 1 状态 |
|---|------|--------|------------|
| 10.1 | 后端启动时间 | < 30s | 待测 |
| 10.2 | 前端首屏加载 | < 3s | 待测 |
| 10.3 | GET 接口 p95 响应 | < 200ms | 待测 |
| 10.4 | POST 接口 p95 响应 | < 500ms | 待测 |
| 10.5 | JVM 堆使用 | < 512MB | 待测 |
| 10.6 | Docker 镜像大小 | < 300MB | 待测 |

> **Phase 1**：指标项留位 + 空模板；Phase 2+ 启动性能压测。

---

*视图版本：v1.0*
*最后更新：2026-06-11*
