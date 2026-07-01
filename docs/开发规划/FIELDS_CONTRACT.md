# 字段完全性契约 (FIELDS CONTRACT)

> 生成时间: 2026-06-24

> 实体数: 174 | Vue 视图数: 50 | Controller 数: 53 | API 文件数: 41

---

## 总览

- ✅ 前后端匹配: 130 字段

- ⚠️ 前端孤儿 (有前端引用无后端实体): 15 字段

- 后端实体字段总数: 1617

- 前端引用字段总数: 356


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

| 4 | Attachment | `attachableId` () | ? | ❌ UNMANAGED | No controller or list page for Attachable | **需修复** |

| 5 | Attachment | `uploaderId` () | ? | ❌ UNMANAGED | No controller or list page for Uploader | **需修复** |

| 6 | BadgeVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 7 | CartItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 8 | CartItem | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 9 | Certificate | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 10 | Certificate | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 11 | Certificate | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 12 | CertificateVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 13 | CertificateVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 14 | ChapterCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 15 | ChapterVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 16 | CheckIn | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 17 | CheckInVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 18 | ClassCreateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 19 | ClassSchedule | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 20 | ClassStudentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 21 | ClassStudentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 22 | ClassUpdateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 23 | ClassVO | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 24 | Classes | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 25 | CommentCreateRequest | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 26 | CompletionWarningVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 27 | Course | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 28 | Course | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 29 | Course | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 30 | CourseBundle | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 31 | CourseBundleItem | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 32 | CourseBundleItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 33 | CourseChapter | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 34 | CourseCreateRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 35 | CourseCreateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 36 | CourseCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 37 | CourseFavorite | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 38 | CourseFavorite | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 39 | CourseFavoriteVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 40 | CourseFavoriteVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 41 | CourseNote | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 42 | CourseNote | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 43 | CourseNote | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 44 | CourseNote | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 45 | CoursePageQuery | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 46 | CoursePageQuery | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 47 | CoursePrerequisite | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 48 | CoursePrerequisite | `prerequisiteCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Prerequisitecourse | **需修复** |

| 49 | CourseReview | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 50 | CourseReview | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 51 | CourseReviewLog | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 52 | CourseReviewLog | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 53 | CourseReviewLogVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 54 | CourseReviewLogVO | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 55 | CourseReviewVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 56 | CourseReviewVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 57 | CourseSlide | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 58 | CourseStatsVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 59 | CourseTagRelation | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 60 | CourseTagRelation | `tagId` () | ? | ❌ UNMANAGED | No controller or list page for Tag | **需修复** |

| 61 | CourseUpdateRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 62 | CourseUpdateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 63 | CourseUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 64 | CourseVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 65 | CourseVO | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 66 | CourseVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 67 | DepartmentDetailVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 68 | DepartmentStatsVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 69 | DiscussionComment | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 70 | DiscussionComment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 71 | DiscussionCommentVO | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 72 | DiscussionCommentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 73 | DiscussionPageQuery | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 74 | DiscussionPost | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 75 | DiscussionPost | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 76 | DiscussionPost | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 77 | DiscussionPostVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 78 | DiscussionPostVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 79 | DiscussionPostVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 80 | Enrollment | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 81 | Enrollment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 82 | EnrollmentCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 83 | EnrollmentCreateRequest | `userId` (userId 由 Controller 从 JWT 自动填充，前端无需传) | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 84 | EnrollmentHistory | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 85 | EnrollmentHistory | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 86 | EnrollmentQueryRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 87 | EnrollmentRankingVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 88 | EnrollmentVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 89 | EnrollmentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 90 | Exercise | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 91 | Exercise | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 92 | ExerciseChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 93 | ExerciseChapter | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 94 | ExerciseCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 95 | ExerciseCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 96 | ExerciseCreateRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 97 | ExerciseQuestion | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 98 | ExerciseQuestion | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 99 | ExerciseRecord | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 100 | ExerciseRecord | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 101 | ExerciseRecordVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 102 | ExerciseRecordVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 103 | ExerciseUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 104 | ExerciseUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 105 | ExerciseUpdateRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 106 | ExerciseVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 107 | ExerciseVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 108 | ExerciseVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 109 | ExerciseVO | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 110 | Grade | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 111 | Grade | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 112 | Grade | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 113 | GradeComponent | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 114 | GradeCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 115 | GradeCreateRequest | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 116 | GradeCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 117 | GradeTeacherSubmitRequest | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 118 | GradeVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 119 | GradeVO | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 120 | GradeVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 121 | GradeVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 122 | LearningProgress | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 123 | LearningProgress | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 124 | LearningProgress | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 125 | LearningProgress | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 126 | LearningProgress | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 127 | LearningProgressVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 128 | LearningProgressVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 129 | LearningProgressVO | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 130 | LearningProgressVO | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 131 | LearningProgressVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 132 | Lesson | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 133 | Lesson | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 134 | Major | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 135 | MajorCreateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 136 | MajorUpdateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 137 | MajorVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 138 | MicroSpecialty | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 139 | MicroSpecialty | `leadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Leadteacher | **需修复** |

| 140 | MicroSpecialty | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 141 | MicroSpecialtyCourse | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 142 | MicroSpecialtyCourse | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 143 | MicroSpecialtyEnrollment | `certificateId` () | ? | ❌ UNMANAGED | No controller or list page for Certificate | **需修复** |

| 144 | MicroSpecialtyEnrollment | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 145 | MicroSpecialtyEnrollment | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 146 | MicroSpecialtyEnrollment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 147 | MicroSpecialtyFeaturedAudit | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 148 | MicroSpecialtyFeaturedAudit | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 149 | MicroSpecialtyProposal | `createdMicroSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Createdmicrospecialty | **需修复** |

| 150 | MicroSpecialtyProposal | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 151 | MicroSpecialtyProposal | `proposerId` () | ? | ❌ UNMANAGED | No controller or list page for Proposer | **需修复** |

| 152 | MicroSpecialtyTeacher | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 153 | MicroSpecialtyTeacher | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 154 | MicroSpecialtyTeacher | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 155 | NarrationSetting | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 156 | Notification | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 157 | Notification | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 158 | NotificationCreateRequest | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 159 | NotificationCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 160 | NotificationPreference | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 161 | NotificationVO | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 162 | NotificationVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 163 | OperationLog | `targetId` () | ? | ❌ UNMANAGED | No controller or list page for Target | **需修复** |

| 164 | OperationLog | `traceId` () | ? | ❌ UNMANAGED | No controller or list page for Trace | **需修复** |

| 165 | OperationLog | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 166 | OperationLogVO | `targetId` () | ? | ❌ UNMANAGED | No controller or list page for Target | **需修复** |

| 167 | OperationLogVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 168 | Order | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 169 | Order | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 170 | Order | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 171 | Payment | `orderId` () | ? | ❌ UNMANAGED | No controller or list page for Order | **需修复** |

| 172 | Payment | `transactionId` () | ? | ❌ UNMANAGED | No controller or list page for Transaction | **需修复** |

| 173 | PluginGrant | `granteeId` () | ? | ❌ UNMANAGED | No controller or list page for Grantee | **需修复** |

| 174 | PluginGrant | `pluginId` () | ? | ❌ UNMANAGED | No controller or list page for Plugin | **需修复** |

| 175 | PostCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 176 | PostCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 177 | PreferenceVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 178 | ProgressCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 179 | ProgressCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 180 | ProgressCreateRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 181 | ProgressCreateRequest | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 182 | ProgressCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 183 | ProgressUpdateRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 184 | ProgressUpdateRequest | `lessonId` () | ? | ❌ UNMANAGED | No controller or list page for Lesson | **需修复** |

| 185 | Question | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 186 | Question | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 187 | QuestionChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 188 | QuestionChapter | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 189 | QuestionCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 190 | QuestionCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 191 | QuestionUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 192 | QuestionUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 193 | QuestionVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 194 | QuestionVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 195 | QuestionVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 196 | ScoreHistory | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 197 | ScoreHistory | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 198 | SlidePage | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 199 | SlidePage | `slideId` () | ? | ❌ UNMANAGED | No controller or list page for Slide | **需修复** |

| 200 | StudentDetailVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 201 | SubmitAnswerRequest | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 202 | SubmitAnswerRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 203 | SubmitAnswerRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 204 | TeachingClass | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 205 | TeachingClass | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 206 | TeachingClassCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 207 | TeachingClassCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 208 | TeachingClassStudent | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 209 | TeachingClassStudent | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 210 | TeachingClassStudentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 211 | TeachingClassStudentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 212 | TeachingClassUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 213 | TeachingClassUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 214 | TeachingClassVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 215 | TeachingClassVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 216 | User | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 217 | User | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 218 | User | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 219 | UserCreateRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 220 | UserCreateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 221 | UserCreateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 222 | UserFollow | `followerId` () | ? | ❌ UNMANAGED | No controller or list page for Follower | **需修复** |

| 223 | UserFollow | `followingId` () | ? | ❌ UNMANAGED | No controller or list page for Following | **需修复** |

| 224 | UserPageQuery | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 225 | UserPageQuery | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 226 | UserPageQuery | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 227 | UserPageQuery | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 228 | UserUpdateRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 229 | UserUpdateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 230 | UserUpdateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 231 | UserVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 232 | UserVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 233 | UserVO | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 234 | Video | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 235 | Video | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 236 | VideoBookmark | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 237 | VideoBookmark | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 238 | VideoBookmarkVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 239 | VideoBookmarkVO | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 240 | VideoCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 241 | VideoCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 242 | VideoUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 243 | VideoVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 244 | VideoVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 245 | WrongQuestion | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 246 | WrongQuestion | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 247 | WrongQuestion | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 248 | WrongQuestionVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 249 | WrongQuestionVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 250 | WrongQuestionVO | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 251 | WrongQuestionVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |



### FK 管理缺口汇总


- ❌ 完全无管理: 251 个

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

| AdminStats | /api/admin/stats | 7 | - |

| Auth | /api/auth | 9 | - |

| Badge | /api/badges | 3 | - |

| BannerPublic | /api/banners | 1 | - |

| Cart | /api/cart | 5 | - |

| Certificate | /api/certificates | 4 | - |

| CheckIn | /api/check-ins | 3 | - |

| Class | /api/classes | 6 | - |

| Course | /api/courses | 16 | - |

| CourseBundle | /api/course-bundles | 6 | - |

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

| MicroSpecialtyTeacher | /api/micro-specialty-teachers | 7 | - |

| MyReview | /api/reviews | 6 | - |

| Notification | /api/notifications | 5 | - |

| NotificationPreference | /api/notification-preferences | 2 | - |

| OperationLog | /api/operation-logs | 1 | - |

| Order | /api/orders | 7 | - |

| Question | /api/questions | 6 | - |

| StorageApplication | /api/storage-applications | 11 | - |

| SystemConfig | /api/system-configs | 1 | - |

| Tag | /api/tags | 7 | - |

| Teacher | /api/teachers | 5 | - |

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

| AdminSetting | 6 | 0 |

| AdminSettingVO | 5 | 0 |

| Attachment | 14 | 0 |

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

| Course | 28 | 0 |

| CourseBundle | 12 | 0 |

| CourseBundleItem | 7 | 0 |

| CourseCategory | 8 | 0 |

| CourseCategoryCreateRequest | 4 | 0 |

| CourseCategoryUpdateRequest | 4 | 0 |

| CourseCategoryVO | 7 | 0 |

| CourseChapter | 12 | 0 |

| CourseCreateRequest | 15 | 0 |

| CourseFavorite | 5 | 0 |

| CourseFavoriteVO | 9 | 0 |

| CourseNote | 11 | 0 |

| CoursePageQuery | 12 | 0 |

| CoursePrerequisite | 6 | 0 |

| CourseReview | 11 | 0 |

| CourseReviewLog | 8 | 0 |

| CourseReviewLogVO | 9 | 0 |

| CourseReviewRequest | 4 | 0 |

| CourseReviewVO | 13 | 0 |

| CourseSlide | 10 | 0 |

| CourseStatsVO | 6 | 0 |

| CourseTagRelation | 4 | 0 |

| CourseTrendVO | 3 | 0 |

| CourseUpdateRequest | 17 | 0 |

| CourseVO | 31 | 0 |

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

| Enrollment | 14 | 0 |

| EnrollmentCreateRequest | 3 | 1 |

| EnrollmentHistory | 7 | 0 |

| EnrollmentQueryRequest | 8 | 0 |

| EnrollmentRankingVO | 6 | 0 |

| EnrollmentUpdateRequest | 5 | 0 |

| EnrollmentVO | 22 | 0 |

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

| MicroSpecialtyEnrollment | 24 | 0 |

| MicroSpecialtyFeaturedAudit | 8 | 0 |

| MicroSpecialtyProposal | 44 | 0 |

| MicroSpecialtyTeacher | 15 | 0 |

| NarrationSetting | 8 | 0 |

| Notification | 11 | 0 |

| NotificationCreateRequest | 8 | 0 |

| NotificationPreference | 8 | 0 |

| NotificationVO | 10 | 0 |

| OperationLog | 12 | 0 |

| OperationLogVO | 14 | 0 |

| Order | 12 | 0 |

| PageResult | 5 | 0 |

| Payment | 8 | 0 |

| PendingTaskVO | 4 | 0 |

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

| TeacherStatsVO | 6 | 0 |

| TeacherStatusRequest | 2 | 0 |

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
