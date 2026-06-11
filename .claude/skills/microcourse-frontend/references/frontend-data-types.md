# 前端 TypeScript 类型定义

> 源文档：`microcourse/references/data-contract.md` v0.5

## 1. User

```ts
interface User {
  id: number
  username: string
  realName: string      // 列表脱敏（如 "张**"）
  email: string         // 列表脱敏（如 "j***@x.edu.cn"）
  phone: string         // 列表脱敏（如 "138****0001"）
  gender: 'MALE' | 'FEMALE'
  avatar: string
  role: 'STUDENT' | 'TEACHER' | 'ADMIN' | 'ACADEMIC'
  departmentId: number | null
  departmentName?: string
  majorId: number | null
  majorName?: string
  classId: number | null
  className?: string
  grade: string | null
  status: number         // 0=INACTIVE, 1=ACTIVE, 2=DISABLED, 3=DELETED
  createdAt: string      // ISO datetime
}
```

## 2. Department

```ts
interface Department {
  id: number
  name: string
  code: string
  parentId: number | null
  parentName?: string
  sortOrder: number
  children?: Department[]   // 树形结构
  createdAt: string
}
```

## 3. Major

```ts
interface Major {
  id: number
  name: string
  code: string
  departmentId: number
  departmentName?: string
  sortOrder: number
  createdAt: string
}
```

## 4. Class

```ts
interface Class {
  id: number
  name: string
  majorId: number
  majorName?: string
  departmentName?: string
  grade: string
  counselorId: number | null
  counselorName?: string
  sortOrder: number
  createdAt: string
}
```

## 5. 分页

```ts
interface PageResult<T> {
  items: T[]
  page: number           // 0-based
  size: number
  totalElements: number   // ← 注意：不是 total
  totalPages: number
}
```

## 6. 统一响应

```ts
interface ApiResponse<T> {
  code: number            // 200 = 成功
  message: string         // "ok" = 成功
  data: T
  timestamp: number       // 毫秒时间戳
}
```

## 7. 枚举映射

```ts
const STATUS_MAP: Record<number, string> = {
  0: '未激活', 1: '正常', 2: '禁用', 3: '已删除'
}
const ROLE_MAP: Record<string, string> = {
  STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', ACADEMIC: '教务处'
}
const GENDER_MAP: Record<string, string> = {
  MALE: '男', FEMALE: '女'
}

// Element Plus tag type 映射
const STATUS_TAG: Record<number, string> = {
  0: 'info', 1: 'success', 2: 'danger', 3: 'info'
}
const ROLE_TAG: Record<string, string> = {
  STUDENT: '', TEACHER: 'warning', ADMIN: 'danger', ACADEMIC: 'success'
}
```
