# 字段完全性契约 (FIELDS CONTRACT)

> 生成时间: 2026-06-24

> 实体数: 192 | Vue 视图数: 54 | Controller 数: 56 | API 文件数: 44

---

## 总览

- ✅ 前后端匹配: 146 字段

- ⚠️ 前端孤儿 (有前端引用无后端实体): 16 字段

- 后端实体字段总数: 1797

- 前端引用字段总数: 383


## ⚠️ 前端孤儿字段


这些字段在前端表单/表格中出现，但**在任意实体类中都不存在**。可能是：

1. 拼写错误 (实体字段名写错了)

2. 已废弃字段 (曾有过但被删了)

3. 计算字段 (前端自行维护)


### 可疑前端孤儿 (0 个)


| # | 字段名 | 出现位置 | 建议行动 |

|---|--------|---------|---------|

(无)



## 🔗 关联字段 (FK) 管理入口状态


检查每个外键字段对应的实体是否有完整的管理 CRUD。


| # | 实体 | FK 字段 | 关联实体 | 管理状态 | 证据 | 建议 |

|---|------|---------|---------|---------|------|------|

| 1 | Achievement | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 2 | AchievementVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 3 | AddStudentRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 4 | AdminRevenueVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 5 | Attachment | `attachableId` () | ? | ❌ UNMANAGED | No controller or list page for Attachable | **需修复** |

| 6 | Attachment | `uploaderId` () | ? | ❌ UNMANAGED | No controller or list page for Uploader | **需修复** |

| 7 | AttendanceRecord | `sessionId` () | ? | ❌ UNMANAGED | No controller or list page for Session | **需修复** |

| 8 | AttendanceRecord | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 9 | AttendanceRecordVO | `sessionId` () | ? | ❌ UNMANAGED | No controller or list page for Session | **需修复** |

| 10 | AttendanceRecordVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 11 | BadgeVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 12 | CartItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 13 | CartItem | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 14 | Certificate | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 15 | Certificate | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 16 | Certificate | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 17 | CertificateVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 18 | CertificateVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 19 | ChapterCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 20 | ChapterOfflineSession | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 21 | ChapterVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 22 | CheckIn | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 23 | CheckInVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 24 | ClassCreateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 25 | ClassSchedule | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 26 | ClassStudentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 27 | ClassStudentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 28 | ClassUpdateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 29 | ClassVO | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 30 | Classes | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 31 | CommentCreateRequest | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 32 | CompletionWarningVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 33 | Course | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 34 | Course | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 35 | Course | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 36 | CourseBundle | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 37 | CourseBundleItem | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 38 | CourseBundleItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 39 | CourseChapter | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 40 | CourseCreateRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 41 | CourseCreateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 42 | CourseCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 43 | CourseFavorite | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 44 | CourseFavorite | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 45 | CourseFavoriteVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 46 | CourseFavoriteVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 47 | CourseNote | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 48 | CourseNote | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 49 | CourseNote | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 50 | CourseNote | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 51 | CoursePageQuery | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 52 | CoursePageQuery | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 53 | CoursePrerequisite | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 54 | CoursePrerequisite | `prerequisiteCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Prerequisitecourse | **需修复** |

| 55 | CourseReview | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 56 | CourseReview | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 57 | CourseReviewLog | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 58 | CourseReviewLog | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 59 | CourseReviewLogVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 60 | CourseReviewLogVO | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 61 | CourseReviewVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 62 | CourseReviewVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 63 | CourseSlide | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 64 | CourseStatsVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 65 | CourseTagRelation | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 66 | CourseTagRelation | `tagId` () | ? | ❌ UNMANAGED | No controller or list page for Tag | **需修复** |

| 67 | CourseUpdateRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 68 | CourseUpdateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 69 | CourseUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 70 | CourseVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 71 | CourseVO | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 72 | CourseVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 73 | DepartmentDetailVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 74 | DepartmentStatsVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 75 | DiscussionComment | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 76 | DiscussionComment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 77 | DiscussionCommentVO | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 78 | DiscussionCommentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 79 | DiscussionPageQuery | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 80 | DiscussionPost | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 81 | DiscussionPost | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 82 | DiscussionPost | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 83 | DiscussionPostVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 84 | DiscussionPostVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 85 | DiscussionPostVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 86 | Enrollment | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 87 | Enrollment | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 88 | Enrollment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 89 | EnrollmentCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 90 | EnrollmentCreateRequest | `userId` (userId 由 Controller 从 JWT 自动填充，前端无需传) | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 91 | EnrollmentHistory | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 92 | EnrollmentHistory | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 93 | EnrollmentQueryRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 94 | EnrollmentRankingVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 95 | EnrollmentVO | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 96 | EnrollmentVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 97 | EnrollmentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 98 | Exercise | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 99 | Exercise | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 100 | ExerciseChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 101 | ExerciseChapter | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 102 | ExerciseCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 103 | ExerciseCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 104 | ExerciseCreateRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 105 | ExerciseQuestion | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 106 | ExerciseQuestion | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 107 | ExerciseRecord | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 108 | ExerciseRecord | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 109 | ExerciseRecordVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 110 | ExerciseRecordVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 111 | ExerciseUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 112 | ExerciseUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 113 | ExerciseUpdateRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 114 | ExerciseVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 115 | ExerciseVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 116 | ExerciseVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 117 | ExerciseVO | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 118 | Grade | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 119 | Grade | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 120 | Grade | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 121 | GradeComponent | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 122 | GradeCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 123 | GradeCreateRequest | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 124 | GradeCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 125 | GradeTeacherSubmitRequest | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 126 | GradeVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 127 | GradeVO | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 128 | GradeVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 129 | GradeVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 130 | LearningProgress | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 131 | LearningProgress | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 132 | LearningProgress | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 133 | LearningProgress | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 134 | LearningProgress | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 135 | LearningProgressVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 136 | LearningProgressVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 137 | LearningProgressVO | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 138 | LearningProgressVO | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 139 | LearningProgressVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 140 | Lesson | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 141 | Lesson | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 142 | Major | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 143 | MajorCreateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 144 | MajorUpdateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 145 | MajorVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 146 | MicroSpecialty | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 147 | MicroSpecialty | `leadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Leadteacher | **需修复** |

| 148 | MicroSpecialty | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 149 | MicroSpecialtyCourse | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 150 | MicroSpecialtyCourse | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 151 | MicroSpecialtyCourseChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 152 | MicroSpecialtyCourseChapter | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 153 | MicroSpecialtyCourseChapter | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 154 | MicroSpecialtyCourseChapter | `proposalChapterId` () | ? | ❌ UNMANAGED | No controller or list page for Proposalchapter | **需修复** |

| 155 | MicroSpecialtyEnrollment | `certificateId` () | ? | ❌ UNMANAGED | No controller or list page for Certificate | **需修复** |

| 156 | MicroSpecialtyEnrollment | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 157 | MicroSpecialtyEnrollment | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 158 | MicroSpecialtyEnrollment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 159 | MicroSpecialtyFeaturedAudit | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 160 | MicroSpecialtyFeaturedAudit | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 161 | MicroSpecialtyProposal | `createdMicroSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Createdmicrospecialty | **需修复** |

| 162 | MicroSpecialtyProposal | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 163 | MicroSpecialtyProposal | `proposerId` () | ? | ❌ UNMANAGED | No controller or list page for Proposer | **需修复** |

| 164 | MicroSpecialtyTeacher | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 165 | MicroSpecialtyTeacher | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 166 | MicroSpecialtyTeacher | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 167 | NarrationSetting | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 168 | Notification | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 169 | Notification | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 170 | NotificationCreateRequest | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 171 | NotificationCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 172 | NotificationPreference | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 173 | NotificationVO | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 174 | NotificationVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 175 | OfflineSessionVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 176 | OperationLog | `targetId` () | ? | ❌ UNMANAGED | No controller or list page for Target | **需修复** |

| 177 | OperationLog | `traceId` () | ? | ❌ UNMANAGED | No controller or list page for Trace | **需修复** |

| 178 | OperationLog | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 179 | OperationLogVO | `targetId` () | ? | ❌ UNMANAGED | No controller or list page for Target | **需修复** |

| 180 | OperationLogVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 181 | Order | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 182 | Order | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 183 | Order | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 184 | Payment | `orderId` () | ? | ❌ UNMANAGED | No controller or list page for Order | **需修复** |

| 185 | Payment | `transactionId` () | ? | ❌ UNMANAGED | No controller or list page for Transaction | **需修复** |

| 186 | PluginGrant | `granteeId` () | ? | ❌ UNMANAGED | No controller or list page for Grantee | **需修复** |

| 187 | PluginGrant | `pluginId` () | ? | ❌ UNMANAGED | No controller or list page for Plugin | **需修复** |

| 188 | PostCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 189 | PostCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 190 | PreferenceVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 191 | ProgressCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 192 | ProgressCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 193 | ProgressCreateRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 194 | ProgressCreateRequest | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 195 | ProgressCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 196 | ProgressUpdateRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 197 | ProgressUpdateRequest | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 198 | Question | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 199 | Question | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 200 | QuestionChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 201 | QuestionChapter | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 202 | QuestionCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 203 | QuestionCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 204 | QuestionUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 205 | QuestionUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 206 | QuestionVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 207 | QuestionVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 208 | QuestionVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 209 | ScoreHistory | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 210 | ScoreHistory | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 211 | SlidePage | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 212 | SlidePage | `slideId` () | ? | ❌ UNMANAGED | No controller or list page for Slide | **需修复** |

| 213 | StudentDetailVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 214 | SubmitAnswerRequest | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 215 | SubmitAnswerRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 216 | SubmitAnswerRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 217 | TeacherRating | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 218 | TeacherRatingVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 219 | TeacherRevenueVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 220 | TeacherTierLog | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 221 | TeacherTierLog | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 222 | TeachingClass | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 223 | TeachingClass | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 224 | TeachingClassCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 225 | TeachingClassCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 226 | TeachingClassStudent | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 227 | TeachingClassStudent | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 228 | TeachingClassStudentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 229 | TeachingClassStudentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 230 | TeachingClassUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 231 | TeachingClassUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 232 | TeachingClassVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 233 | TeachingClassVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 234 | User | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 235 | User | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 236 | User | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 237 | UserCreateRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 238 | UserCreateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 239 | UserCreateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 240 | UserFollow | `followerId` () | ? | ❌ UNMANAGED | No controller or list page for Follower | **需修复** |

| 241 | UserFollow | `followingId` () | ? | ❌ UNMANAGED | No controller or list page for Following | **需修复** |

| 242 | UserPageQuery | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 243 | UserPageQuery | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 244 | UserPageQuery | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 245 | UserPageQuery | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 246 | UserUpdateRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 247 | UserUpdateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 248 | UserUpdateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 249 | UserVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 250 | UserVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 251 | UserVO | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 252 | Video | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 253 | Video | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 254 | VideoBookmark | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 255 | VideoBookmark | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 256 | VideoBookmarkVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 257 | VideoBookmarkVO | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 258 | VideoCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 259 | VideoCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 260 | VideoUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 261 | VideoVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 262 | VideoVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 263 | WrongQuestion | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 264 | WrongQuestion | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 265 | WrongQuestion | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 266 | WrongQuestionVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 267 | WrongQuestionVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 268 | WrongQuestionVO | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 269 | WrongQuestionVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |



### FK 管理缺口汇总


- ❌ 完全无管理: 269 个

- ⚠️ 有 API 无前端: 0 个

- ⚠️ 有前端无 API: 0 个

- ✅ 有完整管理: 其余


## 🚨 优先修复排行榜 (按业务影响)


| 排名 | 字段 | 实体 | 问题 | 修复方案 | 成本 |

|------|------|------|------|---------|------|

| 1 | `counselorId` | Classes | ⚠️ 用 TEACHER 替代 | 加 COUNSELOR 角色或删字段 | 1-2 天 |

| 2 | `teacherId` | Course | ⚠️ 手输数字 | 改 el-select + 屏蔽输入 | 30 分钟 |

| 3 | `collegeId` | MicroSpecialtyList | ❌ 字段名错 | 统一为 offerDepartmentId | 30 分钟 |

| 4 | `offerDepartmentId` | MicroSpecialty | 命名不一致 | 统一为 departmentId | 1 小时 |

| 5 | `teacherNo` | User | ⚠️ 只显不编 | 加编辑弹窗 input | 2 小时 |

| 6+ | (详见 FK 表) | | 管理入口缺失 | 补 CRUD 或删字段 | 不定 |


## 📋 Controller 清单 (管理入口基线)


| Controller | 基础路径 | 方法数 | 覆盖实体 |

|-----------|---------|--------|--------|

| AcademicStats | /api/academic/stats | 6 | - |

| AdminBanner | /api/admin/banners | 5 | - |

| AdminSettings | /api/admin/settings | 6 | - |

| AdminStats | /api/admin/stats | 8 | - |

| Auth | /api/auth | 9 | - |

| Badge | /api/badges | 3 | - |

| BannerPublic | /api/banners | 1 | - |

| Cart | /api/cart | 5 | - |

| Certificate | /api/certificates | 4 | - |

| CheckIn | /api/check-ins | 3 | - |

| Class | /api/classes | 6 | - |

| Course | /api/courses | 21 | - |

| CourseBundle | /api/course-bundles | 8 | - |

| CourseCategory | /api/course-categories | 5 | - |

| CourseChapter | /api/chapters | 6 | - |

| CourseFavorite | /api/favorites | 4 | - |

| CourseReview | /api/courses/{id}/reviews | 2 | - |

| CourseReviewLog | /api/course-review-logs | 1 | - |

| Department | /api/departments | 6 | - |

| DiscussionAdmin | /api/admin/discussions | 5 | - |

| DiscussionComment | /api/discussions | 4 | - |

| DiscussionPost | /api/discussions/posts | 8 | - |

| Enrollment | /api/enrollments | 10 | - |

| EnumExport | /api/enums | 1 | - |

| Exam | /api/exams | 1 | - |

| Exercise | /api/exercises | 11 | - |

| ExerciseRecord | /api/exercise-records | 6 | - |

| FrontendError | /api/frontend-errors | 1 | - |

| Grade | /api/grades | 10 | - |

| LearningProgress | /api/learning-progress | 8 | - |

| Lesson | /api/lessons | 6 | - |

| Major | /api/majors | 5 | - |

| MicroSpecialty | /api/micro-specialties | 24 | - |

| MicroSpecialtyEnrollment | /api/micro-specialty-enrollments | 8 | - |

| MicroSpecialtyFeatured | /api/micro-specialties | 6 | - |

| MicroSpecialtyProposal | /api/micro-specialty-proposals | 10 | - |

| MicroSpecialtyTeacher | /api/micro-specialty-teachers | 8 | - |

| MyReview | /api/reviews | 6 | - |

| Notification | /api/notifications | 5 | - |

| NotificationPreference | /api/notification-preferences | 2 | - |

| OfflineSession | /api | 8 | - |

| OperationLog | /api/operation-logs | 1 | - |

| Order | /api/orders | 7 | - |

| PlatformShareConfig | /api/admin/platform-share-config | 3 | - |

| Question | /api/questions | 6 | - |

| StorageApplication | /api/storage-applications | 11 | - |

| SystemConfig | /api/system-configs | 1 | - |

| Tag | /api/tags | 7 | - |

| Teacher | /api/teachers | 6 | - |

| TeacherRating | /api/teacher-ratings | 7 | - |

| TeachingClass | /api/teaching-classes | 12 | - |

| User | /api/users | 9 | - |

| Video | /api/videos | 11 | - |

| VideoBookmark | /api/videos/{videoId}/bookmarks | 3 | - |

| VideoStream | /api/video-stream | 1 | - |

| WrongQuestion | /api/wrong-questions | 1 | - |



## 📊 实体字段统计


| 实体 | 字段数 | FK 字段 |

|------|--------|--------|

| AcademicOverviewVO | 7 | 0 |

| Achievement | 6 | 0 |

| AchievementVO | 5 | 0 |

| AddStudentRequest | 1 | 0 |

| AdminRevenueVO | 14 | 0 |

| AdminSetting | 6 | 0 |

| AdminSettingVO | 5 | 0 |

| Attachment | 14 | 0 |

| AttendanceRecord | 8 | 0 |

| AttendanceRecordVO | 7 | 0 |

| AttendanceUpdateRequest | 1 | 0 |

| BadgeDefinition | 8 | 0 |

| BadgeDefinitionVO | 8 | 0 |

| BadgeVO | 5 | 0 |

| Banner | 8 | 0 |

| BannerVO | 7 | 0 |

| BatchImportResultVO | 6 | 0 |

| CartItem | 7 | 0 |

| CasSettingsDTO | 7 | 0 |

| Certificate | 8 | 0 |

| CertificateVO | 7 | 0 |

| ChangePasswordRequest | 2 | 0 |

| ChapterCreateRequest | 6 | 0 |

| ChapterOfflineSession | 12 | 0 |

| ChapterSortRequest | 2 | 0 |

| ChapterUpdateRequest | 5 | 0 |

| ChapterVO | 14 | 0 |

| CheckIn | 7 | 0 |

| CheckInVO | 6 | 0 |

| ClassCreateRequest | 4 | 0 |

| ClassSchedule | 10 | 0 |

| ClassScheduleDTO | 8 | 0 |

| ClassStudentVO | 6 | 0 |

| ClassUpdateRequest | 4 | 0 |

| ClassVO | 7 | 0 |

| Classes | 8 | 0 |

| CommentCreateRequest | 4 | 0 |

| CompletionWarningVO | 6 | 0 |

| Course | 36 | 0 |

| CourseBundle | 13 | 0 |

| CourseBundleItem | 8 | 0 |

| CourseCategory | 8 | 0 |

| CourseCategoryCreateRequest | 4 | 0 |

| CourseCategoryUpdateRequest | 4 | 0 |

| CourseCategoryVO | 7 | 0 |

| CourseChapter | 12 | 0 |

| CourseCreateRequest | 19 | 0 |

| CourseFavorite | 5 | 0 |

| CourseFavoriteVO | 9 | 0 |

| CourseNote | 11 | 0 |

| CoursePageQuery | 12 | 0 |

| CoursePrerequisite | 6 | 0 |

| CoursePricingInfoVO | 8 | 0 |

| CoursePricingRequest | 5 | 0 |

| CourseReview | 11 | 0 |

| CourseReviewLog | 8 | 0 |

| CourseReviewLogVO | 9 | 0 |

| CourseReviewRequest | 4 | 0 |

| CourseReviewVO | 13 | 0 |

| CourseSlide | 10 | 0 |

| CourseStatsVO | 6 | 0 |

| CourseTagRelation | 4 | 0 |

| CourseTrendVO | 3 | 0 |

| CourseUpdateRequest | 21 | 0 |

| CourseVO | 36 | 0 |

| DailyActivityVO | 2 | 0 |

| DashboardOverviewVO | 10 | 0 |

| Department | 8 | 0 |

| DepartmentCreateRequest | 4 | 0 |

| DepartmentDetailVO | 7 | 0 |

| DepartmentStatsVO | 8 | 0 |

| DepartmentUpdateRequest | 4 | 0 |

| DepartmentVO | 8 | 0 |

| DiscussionComment | 12 | 0 |

| DiscussionCommentVO | 13 | 0 |

| DiscussionPageQuery | 5 | 0 |

| DiscussionPost | 15 | 0 |

| DiscussionPostVO | 17 | 0 |

| Enrollment | 15 | 0 |

| EnrollmentCreateRequest | 3 | 1 |

| EnrollmentHistory | 7 | 0 |

| EnrollmentQueryRequest | 8 | 0 |

| EnrollmentRankingVO | 6 | 0 |

| EnrollmentUpdateRequest | 5 | 0 |

| EnrollmentVO | 23 | 0 |

| Exercise | 17 | 0 |

| ExerciseChapter | 2 | 0 |

| ExerciseCreateRequest | 14 | 0 |

| ExerciseQuestion | 5 | 0 |

| ExerciseRecord | 12 | 0 |

| ExerciseRecordVO | 11 | 0 |

| ExerciseUpdateRequest | 14 | 0 |

| ExerciseVO | 30 | 0 |

| Grade | 17 | 0 |

| GradeComponent | 8 | 0 |

| GradeCreateRequest | 9 | 0 |

| GradeTeacherSubmitRequest | 3 | 0 |

| GradeUpdateRequest | 5 | 0 |

| GradeVO | 19 | 0 |

| LearningProgress | 20 | 0 |

| LearningProgressVO | 22 | 0 |

| Lesson | 12 | 0 |

| LoginRequest | 2 | 0 |

| LoginResponse | 4 | 0 |

| Major | 8 | 0 |

| MajorCreateRequest | 4 | 0 |

| MajorUpdateRequest | 4 | 0 |

| MajorVO | 7 | 0 |

| MicroSpecialty | 41 | 0 |

| MicroSpecialtyCourse | 11 | 0 |

| MicroSpecialtyCourseChapter | 8 | 0 |

| MicroSpecialtyEnrollment | 24 | 0 |

| MicroSpecialtyFeaturedAudit | 8 | 0 |

| MicroSpecialtyProposal | 44 | 0 |

| MicroSpecialtyTeacher | 15 | 0 |

| NarrationSetting | 8 | 0 |

| Notification | 11 | 0 |

| NotificationCreateRequest | 8 | 0 |

| NotificationPreference | 8 | 0 |

| NotificationVO | 10 | 0 |

| OfflineSessionCreateRequest | 6 | 0 |

| OfflineSessionUpdateRequest | 6 | 0 |

| OfflineSessionVO | 16 | 0 |

| OperationLog | 12 | 0 |

| OperationLogVO | 14 | 0 |

| Order | 12 | 0 |

| PageResult | 5 | 0 |

| Payment | 8 | 0 |

| PendingTaskVO | 4 | 0 |

| PlatformShareConfig | 7 | 0 |

| PlatformShareConfigDTO | 7 | 0 |

| PluginGrant | 5 | 0 |

| PostCreateRequest | 5 | 0 |

| PostUpdateRequest | 3 | 0 |

| PreferenceUpdateRequest | 5 | 0 |

| PreferenceVO | 8 | 0 |

| ProgressCreateRequest | 14 | 0 |

| ProgressUpdateRequest | 12 | 0 |

| Question | 14 | 0 |

| QuestionChapter | 2 | 0 |

| QuestionCreateRequest | 12 | 0 |

| QuestionUpdateRequest | 11 | 0 |

| QuestionVO | 20 | 0 |

| R | 4 | 0 |

| RefreshRequest | 1 | 0 |

| RegisterRequest | 2 | 0 |

| ScoreHistory | 9 | 0 |

| SettingUpdateRequest | 2 | 0 |

| SlidePage | 17 | 0 |

| StudentActivityVO | 4 | 0 |

| StudentDetailVO | 7 | 0 |

| SubmitAnswerRequest | 7 | 0 |

| Tag | 5 | 0 |

| TagCreateRequest | 2 | 0 |

| TagUpdateRequest | 2 | 0 |

| TagVO | 4 | 0 |

| TeacherCourseVO | 6 | 0 |

| TeacherNotificationVO | 4 | 0 |

| TeacherRating | 11 | 0 |

| TeacherRatingVO | 11 | 0 |

| TeacherRevenueVO | 13 | 0 |

| TeacherStatsVO | 6 | 0 |

| TeacherStatusRequest | 2 | 0 |

| TeacherTierLog | 8 | 0 |

| TeacherTierLogVO | 7 | 0 |

| TeachingClass | 14 | 0 |

| TeachingClassCreateRequest | 8 | 0 |

| TeachingClassStudent | 5 | 0 |

| TeachingClassStudentVO | 9 | 0 |

| TeachingClassUpdateRequest | 9 | 0 |

| TeachingClassVO | 17 | 0 |

| TrendPointVO | 2 | 0 |

| UpdateProfileRequest | 4 | 0 |

| UpdateStudentStatusRequest | 1 | 0 |

| User | 26 | 0 |

| UserBatchImportDTO | 7 | 0 |

| UserCreateRequest | 17 | 0 |

| UserFollow | 4 | 0 |

| UserPageQuery | 10 | 0 |

| UserStatusRequest | 1 | 0 |

| UserTrendVO | 3 | 0 |

| UserUpdateRequest | 15 | 0 |

| UserVO | 27 | 0 |

| Video | 30 | 0 |

| VideoBookmark | 7 | 0 |

| VideoBookmarkCreateRequest | 3 | 0 |

| VideoBookmarkVO | 7 | 0 |

| VideoCreateRequest | 7 | 0 |

| VideoUpdateRequest | 4 | 0 |

| VideoVO | 23 | 0 |

| WrongQuestion | 7 | 0 |

| WrongQuestionVO | 15 | 0 |


---

> 此契约为准入门禁基线。任何前端新字段必须匹配后端实体，否则 BLOCK。
