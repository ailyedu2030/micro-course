import { EnrollmentStatus } from '@/utils/enums'

const ACTIVE_LEARNING_STATUSES = new Set([
  EnrollmentStatus.ENROLLED,
  EnrollmentStatus.APPROVED
])

const COURSE_COLLECTION_STATUSES = new Set([
  EnrollmentStatus.ENROLLED,
  EnrollmentStatus.APPROVED,
  EnrollmentStatus.COMPLETED
])

export function isActiveLearningEnrollment(enrollment) {
  if (!enrollment) return false
  return ACTIVE_LEARNING_STATUSES.has(enrollment.enrollmentStatus)
}

export function isCourseCollectionEnrollment(enrollment) {
  if (!enrollment) return false
  return COURSE_COLLECTION_STATUSES.has(enrollment.enrollmentStatus)
}

export function filterActiveLearningEnrollments(enrollments) {
  return (enrollments || []).filter(isActiveLearningEnrollment)
}

export function filterCourseCollectionEnrollments(enrollments) {
  return (enrollments || []).filter(isCourseCollectionEnrollment)
}
