/**
 * 前端枚举常量（P3-9 · 与后端枚举保持同步的"单一真相"镜像）。
 *
 * 后端真相位置：micro-course-api/src/main/java/com/microcourse/enums/*.java
 * 后端导出端点：GET /api/enums/export（公开），返回 R 包装的全部枚举元数据。
 *
 * 同步方式（任意其一）：
 *   1) 运行时同步：应用启动时调用 {@link syncEnumsFromBackend}，把后端最新枚举写入
 *      window.__BACKEND_ENUMS（运行时优先，后端不可用时自动回退本地）。
 *   2) 手动同步：后端枚举变更后，从 /api/enums/export 复制对应值更新下方常量。
 *
 * 本文件作为 fallback：即使后端不可用（离线/拉取失败），下方常量仍保证前端可用，不影响运行。
 *
 * 值语义约定（与后端字段类型对齐）：
 *   - CourseStatus：值为 code(整数 0-6)，对齐 DB/API 的 status 字段（替代硬编码 status=1 等魔法数字）。
 *   - 其余枚举：值为枚举名字符串，对齐后端 name()/value()。
 *
 * 演示用法（不强制迁移现有组件，避免回归）：
 *   import { CourseStatus, EnrollmentStatus } from '@/utils/enums'
 *   //  旧： if (course.status === 1) { ... }   // 1 = 待审核（魔法数字）
 *   //  新： if (course.status === CourseStatus.PENDING_REVIEW) { ... }
 *   //  旧： if (e.status === 'APPROVED') { ... }
 *   //  新： if (e.status === EnrollmentStatus.APPROVED) { ... }
 *   // 运行时优先（如已 syncEnumsFromBackend）：window.__BACKEND_ENUMS?.CourseStatus
 */

/** 用户角色（值=枚举名，对齐后端 UserRole.name()） */
export const UserRole = {
  STUDENT: 'STUDENT',
  TEACHER: 'TEACHER',
  ADMIN: 'ADMIN',
  ACADEMIC: 'ACADEMIC'
}

/** 课程状态（值=code 整数，对齐后端 CourseStatus.getCode() 与 DB/API status 字段） */
export const CourseStatus = {
  DRAFT: 0,
  PENDING_REVIEW: 1,
  APPROVED: 2,
  REJECTED: 3,
  PUBLISHED: 4,
  CLOSED: 5,
  ARCHIVED: 6
}

/** 选课状态（值=枚举名/value，对齐后端 EnrollmentStatus.getValue()） */
export const EnrollmentStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  WAITLIST: 'WAITLIST',
  CANCELLED: 'CANCELLED',
  REJECTED: 'REJECTED',
  COMPLETED: 'COMPLETED',
  DROPPED: 'DROPPED',
  /** @deprecated LEGACY 兼容值，后端新写入已逐步迁移为 APPROVED */
  ENROLLED: 'ENROLLED'
}

/** UserStatus（值=code 整数，对齐后端 UserStatus.getCode()） */
export const UserStatus = {
  INACTIVE: 0, ACTIVE: 1, DISABLED: 2, DELETED: 3
}

/** OrderStatus（值=枚举名，对齐后端 OrderStatus.name()） */
export const OrderStatus = {
  PENDING: 'PENDING', PAID: 'PAID',
  CANCELLED: 'CANCELLED', REFUNDED: 'REFUNDED'
}

/** TeachingClassStatus（值=code 整数，对齐后端 TeachingClassStatus.getCode()） */
export const TeachingClassStatus = {
  CANCELLED: 0, ACTIVE: 1, COMPLETED: 2
}

/** MicroSpecialtyStatus（值=枚举名，对齐后端 MicroSpecialtyStatus.name()） */
export const MicroSpecialtyStatus = {
  DRAFT: 'DRAFT', PENDING_REVIEW: 'PENDING_REVIEW',
  APPROVED: 'APPROVED', REJECTED: 'REJECTED',
  RECRUITING: 'RECRUITING', COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED', ARCHIVED: 'ARCHIVED'
}

/** MicroSpecialtyEnrollmentStatus（值=枚举名，对齐后端 MicroSpecialtyEnrollmentStatus.name()） */
export const MicroSpecialtyEnrollmentStatus = {
  PENDING: 'PENDING', APPROVED: 'APPROVED',
  IN_PROGRESS: 'IN_PROGRESS', COMPLETED: 'COMPLETED',
  DROPPED: 'DROPPED', FAILED: 'FAILED',
  REJECTED: 'REJECTED', CERTIFIED: 'CERTIFIED'
}

/** MicroSpecialtyFeaturedStatus（值=枚举名，对齐后端 MicroSpecialtyFeaturedStatus.name()） */
export const MicroSpecialtyFeaturedStatus = {
  NONE: 'NONE', PENDING: 'PENDING',
  APPROVED: 'APPROVED', REJECTED: 'REJECTED'
}

/** MicroSpecialtyTeacherRole（值=枚举名，对齐后端 MicroSpecialtyTeacherRole.name()） */
export const MicroSpecialtyTeacherRole = {
  LEAD: 'LEAD', MEMBER: 'MEMBER', ASSISTANT: 'ASSISTANT'
}

/** MicroSpecialtyProposalStatus（值=枚举名，对齐后端 MicroSpecialtyProposalStatus.name()） */
export const MicroSpecialtyProposalStatus = {
  DRAFT: 'DRAFT', PENDING_REVIEW: 'PENDING_REVIEW',
  APPROVED: 'APPROVED', REJECTED: 'REJECTED', WITHDRAWN: 'WITHDRAWN'
}

/** 通知类型（值=code 字符串，对齐后端 NotificationType.getCode()，持久化进 notifications.type） */
export const NotificationType = {
  ENROLLMENT_SUCCESS: 'ENROLLMENT_SUCCESS',
  EXERCISE_GRADED: 'EXERCISE_GRADED',
  COURSE_APPROVED: 'COURSE_APPROVED',
  COURSE_REJECTED: 'COURSE_REJECTED',
  COURSE_PUBLISHED: 'COURSE_PUBLISHED',
  VIDEO_TRANSCODED: 'VIDEO_TRANSCODED',
  DISCUSSION_REPLY: 'DISCUSSION_REPLY',
  // R8 P0-5: 成绩发布通知（后端 NotificationType.GRADE_ISSUED 镜像）
  GRADE_ISSUED: 'GRADE_ISSUED'
}

/** 性别（值=枚举名，对齐后端 Gender.name()） */
export const Gender = {
  MALE: 'MALE',
  FEMALE: 'FEMALE',
  SECRET: 'SECRET'  // ★ 新增
}

/**
 * 运行时从后端拉取最新枚举元数据并挂到 window.__BACKEND_ENUMS。
 *
 * - 成功：返回后端返回的枚举映射对象（已解包 R 包装的 data 字段）。
 * - 失败（网络/未启动/非 JSON）：捕获并告警，返回 null —— 调用方据此回退本地常量，绝不抛出。
 *
 * 兼容性：同时支持后端返回 R 包装 { code, message, data } 与裸 Map 两种形态。
 *
 * @returns {Promise<Object|null>} 后端枚举映射，或 null（拉取失败）
 */
export async function syncEnumsFromBackend() {
  try {
    // R7 横向扫描修复：与 request.js 保持一致，使用 VITE_API_BASE_URL（fallback /api）
    const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
    const response = await fetch(`${API_BASE_URL}/enums/export`, {
      headers: { Accept: 'application/json' }
    })
    if (!response.ok) {
      console.warn('[enums] backend export returned HTTP', response.status, '- using local fallback')
      return null
    }
    const json = await response.json()
    // 兼容 R 包装（取 data）与裸 Map（取 json 本身）
    const data = json && typeof json === 'object' && json.data ? json.data : json
    return data && typeof data === 'object' ? data : null
  } catch (e) {
    console.warn('[enums] failed to sync from backend, using local fallback:', e)
    return null
  }
}
