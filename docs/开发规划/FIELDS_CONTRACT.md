# 字段完全性契约 (FIELDS CONTRACT)

> 生成时间: 2026-06-24

> 实体数: 284 | Vue 视图数: 60 | Controller 数: 77 | API 文件数: 46

---

## 总览

- ✅ 前后端匹配: 158 字段

- ⚠️ 前端孤儿 (有前端引用无后端实体): 13 字段

- 后端实体字段总数: 2610

- 前端引用字段总数: 428


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

| 1 | AcceptWithChaptersRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 2 | AcceptWithChaptersRequest | `sourceChapterId` ("existing" or "new") | ? | ❌ UNMANAGED | No controller or list page for Sourcechapter | **需修复** |

| 3 | Achievement | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 4 | AchievementVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 5 | AddCourseRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 6 | AddStudentRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 7 | AdminRevenueVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 8 | Attachment | `attachableId` () | ? | ❌ UNMANAGED | No controller or list page for Attachable | **需修复** |

| 9 | Attachment | `uploaderId` () | ? | ❌ UNMANAGED | No controller or list page for Uploader | **需修复** |

| 10 | AttendanceRecord | `sessionId` () | ? | ❌ UNMANAGED | No controller or list page for Session | **需修复** |

| 11 | AttendanceRecord | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 12 | AttendanceRecordVO | `sessionId` () | ? | ❌ UNMANAGED | No controller or list page for Session | **需修复** |

| 13 | AttendanceRecordVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 14 | BadgeVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 15 | BatchApproveRequest | `ids` () | ? | ❌ UNMANAGED | No controller or list page for  | **需修复** |

| 16 | BatchPushScriptsRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 17 | BatchPushScriptsRequest | `sectionId` () | ? | ❌ UNMANAGED | No controller or list page for Section | **需修复** |

| 18 | BatchRejectRequest | `ids` () | ? | ❌ UNMANAGED | No controller or list page for  | **需修复** |

| 19 | BundleItemVO | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 20 | BundleItemVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 21 | BundleVO | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 22 | CartAddRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 23 | CartItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 24 | CartItem | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 25 | Certificate | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 26 | Certificate | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 27 | Certificate | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 28 | Certificate | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 29 | CertificateVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 30 | CertificateVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 31 | ChapterAssignmentItem | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 32 | ChapterAssignmentItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 33 | ChapterAssignmentItem | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 34 | ChapterCreateRequest | `courseId` (服务层 validateCourseId() 兜底处理 null。) | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 35 | ChapterOfflineSession | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 36 | ChapterVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 37 | CheckIn | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 38 | CheckInVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 39 | ClassCreateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 40 | ClassImportRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 41 | ClassImportRequest | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 42 | ClassSchedule | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 43 | ClassStudentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 44 | ClassStudentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 45 | ClassUpdateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 46 | ClassVO | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 47 | Classes | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 48 | CommentCreateRequest | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 49 | CompletionWarningVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 50 | Course | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 51 | Course | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 52 | Course | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 53 | CourseBundle | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 54 | CourseBundleItem | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 55 | CourseBundleItem | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 56 | CourseChapter | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 57 | CourseCreateRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 58 | CourseCreateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 59 | CourseCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 60 | CourseFavorite | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 61 | CourseFavorite | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 62 | CourseFavoriteVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 63 | CourseFavoriteVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 64 | CourseNote | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 65 | CourseNote | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 66 | CourseNote | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 67 | CourseNote | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 68 | CoursePageQuery | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 69 | CoursePageQuery | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 70 | CoursePageQuery | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 71 | CoursePrerequisite | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 72 | CoursePrerequisite | `prerequisiteCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Prerequisitecourse | **需修复** |

| 73 | CourseReview | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 74 | CourseReview | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 75 | CourseReviewLog | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 76 | CourseReviewLog | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 77 | CourseReviewLogVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 78 | CourseReviewLogVO | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 79 | CourseReviewVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 80 | CourseReviewVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 81 | CourseSlide | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 82 | CourseSlide | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 83 | CourseSlide | `sectionId` () | ? | ❌ UNMANAGED | No controller or list page for Section | **需修复** |

| 84 | CourseStatsVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 85 | CourseTagRelation | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 86 | CourseTagRelation | `tagId` () | ? | ❌ UNMANAGED | No controller or list page for Tag | **需修复** |

| 87 | CourseUpdateRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 88 | CourseUpdateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 89 | CourseUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 90 | CourseVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 91 | CourseVO | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 92 | CourseVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 93 | CreateReportRequest | `reportedItemId` () | ? | ❌ UNMANAGED | No controller or list page for Reporteditem | **需修复** |

| 94 | DepartmentDetailVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 95 | DepartmentStatsVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 96 | DiscussionComment | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 97 | DiscussionComment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 98 | DiscussionCommentLike | `commentId` () | ? | ❌ UNMANAGED | No controller or list page for Comment | **需修复** |

| 99 | DiscussionCommentLike | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 100 | DiscussionCommentVO | `postId` () | ? | ❌ UNMANAGED | No controller or list page for Post | **需修复** |

| 101 | DiscussionCommentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 102 | DiscussionPageQuery | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 103 | DiscussionPost | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 104 | DiscussionPost | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 105 | DiscussionPost | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 106 | DiscussionPostVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 107 | DiscussionPostVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 108 | DiscussionPostVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 109 | Enrollment | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 110 | Enrollment | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 111 | Enrollment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 112 | EnrollmentCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 113 | EnrollmentCreateRequest | `userId` (userId 由 Controller 从 JWT 自动填充，前端无需传) | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 114 | EnrollmentHistory | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 115 | EnrollmentHistory | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 116 | EnrollmentQueryRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 117 | EnrollmentRankingVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 118 | EnrollmentVO | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 119 | EnrollmentVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 120 | EnrollmentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 121 | ExamGenerateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 122 | Exercise | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 123 | Exercise | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 124 | ExerciseChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 125 | ExerciseChapter | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 126 | ExerciseCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 127 | ExerciseCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 128 | ExerciseCreateRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 129 | ExerciseQuestion | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 130 | ExerciseQuestion | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 131 | ExerciseRecord | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 132 | ExerciseRecord | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 133 | ExerciseRecordVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 134 | ExerciseRecordVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 135 | ExerciseUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 136 | ExerciseUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 137 | ExerciseUpdateRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 138 | ExerciseVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 139 | ExerciseVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 140 | ExerciseVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 141 | ExerciseVO | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 142 | FavoriteCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 143 | Grade | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 144 | Grade | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 145 | Grade | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 146 | GradeComponent | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 147 | GradeCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 148 | GradeCreateRequest | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 149 | GradeCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 150 | GradeTeacherSubmitRequest | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 151 | GradeVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 152 | GradeVO | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 153 | GradeVO | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 154 | GradeVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 155 | HermesCourseDetailVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 156 | HermesCourseDetailVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 157 | HermesCourseDetailVO | `hermesCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Hermescourse | **需修复** |

| 158 | HermesCourseDetailVO | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 159 | HermesCourseDetailVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 160 | HermesCourseListVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 161 | HermesCourseListVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 162 | HermesCourseListVO | `hermesCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Hermescourse | **需修复** |

| 163 | HermesCourseMapping | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 164 | HermesCourseMapping | `hermesCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Hermescourse | **需修复** |

| 165 | HermesCourseMapping | `hermesTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Hermesteacher | **需修复** |

| 166 | HermesSectionVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 167 | HermesWebhookRequest | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 168 | HermesWebhookRequest | `hermesCourseId` () | ? | ❌ UNMANAGED | No controller or list page for Hermescourse | **需修复** |

| 169 | HermesWebhookRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 170 | HermesWebhookRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 171 | InviteTeacherRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 172 | LearningProgress | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 173 | LearningProgress | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 174 | LearningProgress | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 175 | LearningProgress | `sectionId` () | ? | ❌ UNMANAGED | No controller or list page for Section | **需修复** |

| 176 | LearningProgress | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 177 | LearningProgressVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 178 | LearningProgressVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 179 | LearningProgressVO | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 180 | LearningProgressVO | `sectionId` () | ? | ❌ UNMANAGED | No controller or list page for Section | **需修复** |

| 181 | LearningProgressVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 182 | Major | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 183 | MajorCreateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 184 | MajorUpdateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 185 | MajorVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 186 | ManualGradeRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 187 | MicroSpecialty | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 188 | MicroSpecialty | `leadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Leadteacher | **需修复** |

| 189 | MicroSpecialty | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 190 | MicroSpecialtyApplyRequest | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 191 | MicroSpecialtyClassImportRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 192 | MicroSpecialtyClassImportRequest | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 193 | MicroSpecialtyClassImportResultVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 194 | MicroSpecialtyCourse | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 195 | MicroSpecialtyCourse | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 196 | MicroSpecialtyCourseChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 197 | MicroSpecialtyCourseChapter | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 198 | MicroSpecialtyCourseChapter | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 199 | MicroSpecialtyCourseChapter | `proposalChapterId` () | ? | ❌ UNMANAGED | No controller or list page for Proposalchapter | **需修复** |

| 200 | MicroSpecialtyCourseRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 201 | MicroSpecialtyCourseVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 202 | MicroSpecialtyCourseVO | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 203 | MicroSpecialtyCreateRequest | `leadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Leadteacher | **需修复** |

| 204 | MicroSpecialtyCreateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 205 | MicroSpecialtyEnrollment | `certificateId` () | ? | ❌ UNMANAGED | No controller or list page for Certificate | **需修复** |

| 206 | MicroSpecialtyEnrollment | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 207 | MicroSpecialtyEnrollment | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 208 | MicroSpecialtyEnrollment | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 209 | MicroSpecialtyEnrollmentVO | `certificateId` () | ? | ❌ UNMANAGED | No controller or list page for Certificate | **需修复** |

| 210 | MicroSpecialtyEnrollmentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 211 | MicroSpecialtyEnrollmentVO | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 212 | MicroSpecialtyEnrollmentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 213 | MicroSpecialtyFeaturedAudit | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 214 | MicroSpecialtyFeaturedAudit | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 215 | MicroSpecialtyLeadTransferRequest | `newLeadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Newleadteacher | **需修复** |

| 216 | MicroSpecialtyProposal | `createdMicroSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Createdmicrospecialty | **需修复** |

| 217 | MicroSpecialtyProposal | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 218 | MicroSpecialtyProposal | `proposerId` () | ? | ❌ UNMANAGED | No controller or list page for Proposer | **需修复** |

| 219 | MicroSpecialtyProposalRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 220 | MicroSpecialtyTeacher | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 221 | MicroSpecialtyTeacher | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 222 | MicroSpecialtyTeacher | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 223 | MicroSpecialtyTeacherRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 224 | MicroSpecialtyTeacherRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 225 | MicroSpecialtyTeacherVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 226 | MicroSpecialtyTeacherVO | `microSpecialtyId` () | ? | ❌ UNMANAGED | No controller or list page for Microspecialty | **需修复** |

| 227 | MicroSpecialtyTeacherVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 228 | MicroSpecialtyUpdateRequest | `leadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Leadteacher | **需修复** |

| 229 | MicroSpecialtyUpdateRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 230 | MicroSpecialtyVO | `creatorId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 231 | MicroSpecialtyVO | `leadTeacherId` () | ? | ❌ UNMANAGED | No controller or list page for Leadteacher | **需修复** |

| 232 | MicroSpecialtyVO | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 233 | NarrationSetting | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 234 | NarrationSettingVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 235 | Notification | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 236 | Notification | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 237 | NotificationCreateRequest | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 238 | NotificationCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 239 | NotificationPreference | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 240 | NotificationVO | `relatedId` () | ? | ❌ UNMANAGED | No controller or list page for Related | **需修复** |

| 241 | NotificationVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 242 | OfflineSessionVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 243 | OperationLog | `targetId` () | ? | ❌ UNMANAGED | No controller or list page for Target | **需修复** |

| 244 | OperationLog | `traceId` () | ? | ❌ UNMANAGED | No controller or list page for Trace | **需修复** |

| 245 | OperationLog | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 246 | OperationLogVO | `targetId` () | ? | ❌ UNMANAGED | No controller or list page for Target | **需修复** |

| 247 | OperationLogVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 248 | Order | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 249 | Order | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 250 | Order | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 251 | OrderCreateRequest | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 252 | OrderCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 253 | OrderVO | `bundleId` () | ? | ❌ UNMANAGED | No controller or list page for CourseBundle | **需修复** |

| 254 | OrderVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 255 | OrderVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 256 | Payment | `orderId` () | ? | ❌ UNMANAGED | No controller or list page for Order | **需修复** |

| 257 | Payment | `transactionId` () | ? | ❌ UNMANAGED | No controller or list page for Transaction | **需修复** |

| 258 | PluginGrant | `granteeId` () | ? | ❌ UNMANAGED | No controller or list page for Grantee | **需修复** |

| 259 | PluginGrant | `pluginId` () | ? | ❌ UNMANAGED | No controller or list page for Plugin | **需修复** |

| 260 | PostCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 261 | PostCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 262 | PreferenceVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 263 | ProgressCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 264 | ProgressCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 265 | ProgressCreateRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 266 | ProgressCreateRequest | `sectionId` () | ? | ❌ UNMANAGED | No controller or list page for Section | **需修复** |

| 267 | ProgressCreateRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 268 | ProgressUpdateRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 269 | ProgressUpdateRequest | `sectionId` () | ? | ❌ UNMANAGED | No controller or list page for Section | **需修复** |

| 270 | Question | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 271 | Question | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 272 | QuestionChapter | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 273 | QuestionChapter | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 274 | QuestionCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 275 | QuestionCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 276 | QuestionUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 277 | QuestionUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 278 | QuestionVO | `categoryId` () | ? | ❌ UNMANAGED | No controller or list page for CourseCategory | **需修复** |

| 279 | QuestionVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 280 | QuestionVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 281 | ReviewReport | `reportedItemId` () | ? | ❌ UNMANAGED | No controller or list page for Reporteditem | **需修复** |

| 282 | ReviewReport | `reporterId` () | ? | ❌ UNMANAGED | No controller or list page for Reporter | **需修复** |

| 283 | ReviewReport | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 284 | ReviewReportVO | `reportedItemId` () | ? | ❌ UNMANAGED | No controller or list page for Reporteditem | **需修复** |

| 285 | ReviewReportVO | `reporterId` () | ? | ❌ UNMANAGED | No controller or list page for Reporter | **需修复** |

| 286 | ReviewReportVO | `reviewerId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 287 | ScoreHistory | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 288 | ScoreHistory | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 289 | ScoreHistoryVO | `enrollmentId` () | ? | ❌ UNMANAGED | No controller or list page for Enrollment | **需修复** |

| 290 | ScoreHistoryVO | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 291 | SectionDTO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 292 | SectionDTO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 293 | SlideUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 294 | StorageApplicationSaveRequest | `offerDepartmentId` () | ? | ❌ UNMANAGED | No controller or list page for Offerdepartment | **需修复** |

| 295 | StudentDetailVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 296 | SubmitAnswerRequest | `exerciseId` () | ? | ❌ UNMANAGED | No controller or list page for Exercise | **需修复** |

| 297 | SubmitAnswerRequest | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 298 | SubmitAnswerRequest | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 299 | TeacherActionRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 300 | TeacherRating | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 301 | TeacherRatingVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 302 | TeacherRevenueVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 303 | TeacherTierLog | `operatorId` () | ? | ❌ UNMANAGED | No controller or list page for Operator | **需修复** |

| 304 | TeacherTierLog | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 305 | TeachingClass | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 306 | TeachingClass | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 307 | TeachingClassCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 308 | TeachingClassCreateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 309 | TeachingClassStudent | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 310 | TeachingClassStudent | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 311 | TeachingClassStudentVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 312 | TeachingClassStudentVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 313 | TeachingClassUpdateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 314 | TeachingClassUpdateRequest | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 315 | TeachingClassVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 316 | TeachingClassVO | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 317 | User | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 318 | User | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 319 | User | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 320 | UserCreateRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 321 | UserCreateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 322 | UserCreateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 323 | UserFollow | `followerId` () | ? | ❌ UNMANAGED | No controller or list page for Follower | **需修复** |

| 324 | UserFollow | `followingId` () | ? | ❌ UNMANAGED | No controller or list page for Following | **需修复** |

| 325 | UserPageQuery | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 326 | UserPageQuery | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 327 | UserPageQuery | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 328 | UserPageQuery | `teacherId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 329 | UserUpdateRequest | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 330 | UserUpdateRequest | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 331 | UserUpdateRequest | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 332 | UserVO | `classId` () | ? | ❌ UNMANAGED | No controller or list page for Classes | **需修复** |

| 333 | UserVO | `departmentId` () | ? | ❌ UNMANAGED | No controller or list page for Department | **需修复** |

| 334 | UserVO | `majorId` () | ? | ❌ UNMANAGED | No controller or list page for Major | **需修复** |

| 335 | Video | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 336 | Video | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 337 | VideoAnalyticsVO | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 338 | VideoBookmark | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 339 | VideoBookmark | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 340 | VideoBookmarkVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 341 | VideoBookmarkVO | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 342 | VideoCreateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 343 | VideoCreateRequest | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 344 | VideoProgressReportRequest | `deviceId` () | ? | ❌ UNMANAGED | No controller or list page for Device | **需修复** |

| 345 | VideoStatusVO | `videoId` () | ? | ❌ UNMANAGED | No controller or list page for Video | **需修复** |

| 346 | VideoUpdateRequest | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 347 | VideoVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 348 | VideoVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 349 | WrongQuestion | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 350 | WrongQuestion | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 351 | WrongQuestion | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |

| 352 | WrongQuestionVO | `chapterId` () | ? | ❌ UNMANAGED | No controller or list page for CourseChapter | **需修复** |

| 353 | WrongQuestionVO | `courseId` () | ? | ❌ UNMANAGED | No controller or list page for Course | **需修复** |

| 354 | WrongQuestionVO | `questionId` () | ? | ❌ UNMANAGED | No controller or list page for Question | **需修复** |

| 355 | WrongQuestionVO | `userId` () | ? | ❌ UNMANAGED | No controller or list page for User | **需修复** |



### FK 管理缺口汇总


- ❌ 完全无管理: 355 个

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

| Alias | /api/courses/{courseId} | 5 | - |

| Auth | /api/auth | 13 | - |

| Badge | /api/badges | 3 | - |

| BannerPublic | /api/banners | 1 | - |

| Cart | /api/cart | 5 | - |

| Certificate | /api/certificates | 4 | - |

| CheckIn | /api/check-ins | 3 | - |

| Class | /api/classes | 6 | - |

| Course | /api/courses | 14 | - |

| CourseBundle | /api/course-bundles | 8 | - |

| CourseCategory | /api/course-categories | 5 | - |

| CourseChapter | /api/chapters | 6 | - |

| CourseExercise | /api/courses/{courseId}/exercises | 9 | - |

| CourseFavorite | /api | 7 | - |

| CourseNote | /api/course-notes | 3 | - |

| CourseOfflineSession | /api/courses/{courseId}/offline-sessions | 6 | - |

| CoursePublish | /api/courses | 13 | - |

| CourseReview | /api/courses/{id}/reviews | 5 | - |

| CourseReviewLog | /api/course-review-logs | 1 | - |

| CourseStudent | /api/courses | 2 | - |

| CourseVideo | /api/courses/{courseId}/videos | 7 | - |

| CoursewareDelete | /api/courses/{courseId}/courseware | 6 | - |

| Dashboard | /api/dashboard | 4 | - |

| Department | /api/departments | 7 | - |

| DiscussionAdmin | /api/admin/discussions | 8 | - |

| DiscussionComment | /api/discussions | 4 | - |

| DiscussionPost | /api/discussions/posts | 8 | - |

| Enrollment | /api/enrollments | 11 | - |

| EnumExport | /api/enums | 1 | - |

| Exam | /api/exams | 2 | - |

| Exercise | /api/exercises | 11 | - |

| ExerciseRecord | /api/exercise-records | 6 | - |

| FileAccess | /api/files | 2 | - |

| FrontendError | /api/frontend-errors | 1 | - |

| Grade | /api/grades | 10 | - |

| HermesEvent | /api/hermes/webhook/events | 1 | - |

| HermesWebhook | /api/hermes/webhook | 16 | - |

| InteractiveCourseware | /api/courses/{courseId}/interactive | 4 | - |

| LearningProgress | /api/learning-progress | 8 | - |

| Lesson | /api/lessons | 6 | - |

| Major | /api/majors | 6 | - |

| MicroSpecialty | /api/micro-specialties | 28 | - |

| MicroSpecialtyEnrollment | /api/micro-specialty-enrollments | 8 | - |

| MicroSpecialtyFeatured | /api/micro-specialties | 6 | - |

| MicroSpecialtyProposal | /api/micro-specialty-proposals | 12 | - |

| MicroSpecialtyTeacher | /api/micro-specialty-teachers | 8 | - |

| MyReview | /api/reviews | 6 | - |

| Notification | /api/notifications | 5 | - |

| NotificationPreference | /api/notification-preferences | 2 | - |

| OfflineSession | /api/offline-sessions | 11 | - |

| OperationLog | /api/operation-logs | 2 | - |

| Order | /api/orders | 8 | - |

| PlatformShareConfig | /api/admin/platform-share-config | 3 | - |

| Profile | /api/auth | 5 | - |

| Question | /api/questions | 7 | - |

| Report | /api/reports | 3 | - |

| Section | /api/courses/{courseId}/chapters/{chapterId}/sections | 5 | - |

| SectionSlide | /api/courses/{courseId}/sections/{sectionId} | 1 | - |

| ServerTime | /api | 1 | - |

| StorageApplication | /api/storage-applications | 14 | - |

| Student | /api/students | 3 | - |

| SystemConfig | /api/system-configs | 1 | - |

| Tag | /api/tags | 7 | - |

| Teacher | /api/teachers | 6 | - |

| TeacherRating | /api/teacher-ratings | 7 | - |

| TeachingClass | /api/teaching-classes | 12 | - |

| User | /api/users | 9 | - |

| UserLearningProgressAlias | /api/users | 1 | - |

| Video | /api/videos | 16 | - |

| VideoBookmark | /api/videos/{videoId}/bookmarks | 3 | - |

| VideoStream | /api/video-stream | 1 | - |

| VideoStreamAlias | /api/videos/stream | 1 | - |

| WrongQuestion | /api/wrong-questions | 1 | - |



## 📊 实体字段统计


| 实体 | 字段数 | FK 字段 |

|------|--------|--------|

| AcademicOverviewVO | 7 | 0 |

| AcceptWithChaptersRequest | 4 | 1 |

| Achievement | 6 | 0 |

| AchievementVO | 5 | 0 |

| AddCourseRequest | 3 | 0 |

| AddQuestionsRequest | 1 | 0 |

| AddStudentRequest | 1 | 0 |

| AdminRevenueVO | 14 | 0 |

| AdminSetting | 7 | 0 |

| AdminSettingVO | 6 | 0 |

| Attachment | 14 | 0 |

| AttendanceRecord | 8 | 0 |

| AttendanceRecordVO | 7 | 0 |

| AttendanceUpdateRequest | 1 | 0 |

| AutoSaveResult | 2 | 0 |

| BadgeDefinition | 8 | 0 |

| BadgeDefinitionVO | 8 | 0 |

| BadgeVO | 5 | 0 |

| Banner | 9 | 0 |

| BannerToggleStatusRequest | 1 | 0 |

| BannerVO | 8 | 0 |

| BatchApproveRequest | 1 | 0 |

| BatchImportResultVO | 6 | 0 |

| BatchOperationResult | 6 | 0 |

| BatchOrderRequest | 2 | 0 |

| BatchPushScriptsRequest | 3 | 0 |

| BatchRejectRequest | 2 | 0 |

| BundleCreateRequest | 5 | 0 |

| BundleItemVO | 8 | 0 |

| BundleUpdateRequest | 5 | 0 |

| BundleVO | 13 | 0 |

| CartAddRequest | 2 | 0 |

| CartItem | 8 | 0 |

| CartUpdateRequest | 1 | 0 |

| CasSettingsDTO | 7 | 0 |

| Certificate | 9 | 0 |

| CertificateVO | 7 | 0 |

| ChangePasswordRequest | 2 | 0 |

| ChapterAssignmentItem | 8 | 0 |

| ChapterCreateRequest | 9 | 1 |

| ChapterOfflineSession | 12 | 0 |

| ChapterSortRequest | 3 | 0 |

| ChapterUpdateRequest | 8 | 0 |

| ChapterVO | 19 | 0 |

| CheckIn | 7 | 0 |

| CheckInVO | 6 | 0 |

| ClassCreateRequest | 4 | 0 |

| ClassImportRequest | 3 | 0 |

| ClassSchedule | 10 | 0 |

| ClassScheduleDTO | 8 | 0 |

| ClassStudentVO | 6 | 0 |

| ClassUpdateRequest | 4 | 0 |

| ClassVO | 7 | 0 |

| Classes | 8 | 0 |

| CommentCreateRequest | 4 | 0 |

| CompletionWarningVO | 6 | 0 |

| Course | 43 | 0 |

| CourseBundle | 13 | 0 |

| CourseBundleItem | 8 | 0 |

| CourseCategory | 8 | 0 |

| CourseCategoryCreateRequest | 4 | 0 |

| CourseCategoryUpdateRequest | 4 | 0 |

| CourseCategoryVO | 7 | 0 |

| CourseChapter | 16 | 0 |

| CourseCreateRequest | 26 | 0 |

| CourseFavorite | 5 | 0 |

| CourseFavoriteVO | 8 | 0 |

| CourseNote | 11 | 0 |

| CoursePageQuery | 14 | 0 |

| CoursePrerequisite | 6 | 0 |

| CoursePricingInfoVO | 8 | 0 |

| CoursePricingRequest | 5 | 0 |

| CourseReview | 11 | 0 |

| CourseReviewLog | 8 | 0 |

| CourseReviewLogVO | 9 | 0 |

| CourseReviewRequest | 4 | 0 |

| CourseReviewVO | 14 | 0 |

| CourseSection | 5 | 0 |

| CourseSlide | 12 | 0 |

| CourseStatsVO | 6 | 0 |

| CourseTagRelation | 4 | 0 |

| CourseTrendVO | 3 | 0 |

| CourseUpdateRequest | 26 | 0 |

| CourseVO | 43 | 0 |

| CreateReportRequest | 3 | 0 |

| DailyActivityVO | 2 | 0 |

| DashboardActivityVO | 4 | 0 |

| DashboardOverviewVO | 10 | 0 |

| DashboardProgressVO | 6 | 0 |

| DashboardRevenueVO | 5 | 0 |

| Department | 8 | 0 |

| DepartmentCreateRequest | 4 | 0 |

| DepartmentDetailVO | 7 | 0 |

| DepartmentStatsVO | 8 | 0 |

| DepartmentUpdateRequest | 4 | 0 |

| DepartmentVO | 8 | 0 |

| DiscussionComment | 12 | 0 |

| DiscussionCommentLike | 4 | 0 |

| DiscussionCommentVO | 13 | 0 |

| DiscussionPageQuery | 5 | 0 |

| DiscussionPost | 16 | 0 |

| DiscussionPostVO | 18 | 0 |

| DropRequest | 2 | 0 |

| Enrollment | 15 | 0 |

| EnrollmentCreateRequest | 3 | 1 |

| EnrollmentHistory | 7 | 0 |

| EnrollmentQueryRequest | 8 | 0 |

| EnrollmentRankingVO | 6 | 0 |

| EnrollmentUpdateRequest | 5 | 0 |

| EnrollmentVO | 23 | 0 |

| ExamGenerateRequest | 5 | 0 |

| Exercise | 18 | 0 |

| ExerciseChapter | 2 | 0 |

| ExerciseCreateRequest | 17 | 0 |

| ExerciseQuestion | 5 | 0 |

| ExerciseRecord | 13 | 0 |

| ExerciseRecordVO | 12 | 0 |

| ExerciseUpdateRequest | 15 | 0 |

| ExerciseVO | 31 | 0 |

| ExportValidationResult | 3 | 0 |

| FavoriteCreateRequest | 1 | 0 |

| FeaturedApplyRequest | 1 | 0 |

| FrontendErrorReportRequest | 4 | 0 |

| Grade | 17 | 0 |

| GradeComponent | 8 | 0 |

| GradeCreateRequest | 9 | 0 |

| GradeTeacherSubmitRequest | 3 | 0 |

| GradeUpdateRequest | 5 | 0 |

| GradeVO | 19 | 0 |

| HermesChapterVO | 8 | 0 |

| HermesCourseDetailVO | 40 | 0 |

| HermesCourseListVO | 10 | 0 |

| HermesCourseMapping | 7 | 0 |

| HermesSectionVO | 12 | 0 |

| HermesWebhookRequest | 28 | 0 |

| InviteStatisticsVO | 8 | 0 |

| InviteTeacherRequest | 3 | 0 |

| LearningProgress | 21 | 0 |

| LearningProgressVO | 24 | 0 |

| LoginRequest | 2 | 0 |

| LoginResponse | 4 | 0 |

| Major | 8 | 0 |

| MajorCreateRequest | 4 | 0 |

| MajorUpdateRequest | 4 | 0 |

| MajorVO | 7 | 0 |

| ManualGradeRequest | 3 | 0 |

| MicroSpecialty | 45 | 0 |

| MicroSpecialtyApplyRequest | 1 | 0 |

| MicroSpecialtyClassImportRequest | 3 | 0 |

| MicroSpecialtyClassImportResultVO | 9 | 0 |

| MicroSpecialtyCourse | 11 | 0 |

| MicroSpecialtyCourseChapter | 8 | 0 |

| MicroSpecialtyCourseRequest | 7 | 0 |

| MicroSpecialtyCourseVO | 12 | 0 |

| MicroSpecialtyCreateRequest | 18 | 0 |

| MicroSpecialtyDetailVO | 3 | 0 |

| MicroSpecialtyEnrollment | 25 | 0 |

| MicroSpecialtyEnrollmentVO | 27 | 0 |

| MicroSpecialtyFeaturedApplyRequest | 1 | 0 |

| MicroSpecialtyFeaturedAudit | 8 | 0 |

| MicroSpecialtyFeaturedVO | 13 | 0 |

| MicroSpecialtyLeadTransferRequest | 1 | 0 |

| MicroSpecialtyProposal | 57 | 0 |

| MicroSpecialtyProposalRequest | 9 | 0 |

| MicroSpecialtyRejectRequest | 1 | 0 |

| MicroSpecialtySquareVO | 16 | 0 |

| MicroSpecialtyStatsVO | 8 | 0 |

| MicroSpecialtyTeacher | 16 | 0 |

| MicroSpecialtyTeacherRequest | 5 | 0 |

| MicroSpecialtyTeacherVO | 12 | 0 |

| MicroSpecialtyUpdateRequest | 18 | 0 |

| MicroSpecialtyVO | 42 | 0 |

| NarrationSetting | 8 | 0 |

| NarrationSettingRequest | 4 | 0 |

| NarrationSettingVO | 7 | 0 |

| NarrationUpdateRequest | 1 | 0 |

| Notification | 11 | 0 |

| NotificationCreateRequest | 8 | 0 |

| NotificationPreference | 9 | 0 |

| NotificationVO | 10 | 0 |

| OfflineSessionCreateRequest | 6 | 0 |

| OfflineSessionUpdateRequest | 6 | 0 |

| OfflineSessionVO | 16 | 0 |

| OperationLog | 12 | 0 |

| OperationLogVO | 14 | 0 |

| Order | 12 | 0 |

| OrderCreateRequest | 2 | 0 |

| OrderVO | 14 | 0 |

| PageResult | 5 | 0 |

| PayRequest | 1 | 0 |

| Payment | 8 | 0 |

| PendingTaskVO | 4 | 0 |

| PlatformShareConfig | 8 | 0 |

| PlatformShareConfigDTO | 8 | 0 |

| PluginGrant | 5 | 0 |

| PostCreateRequest | 5 | 0 |

| PostUpdateRequest | 3 | 0 |

| PreferenceUpdateRequest | 6 | 0 |

| PreferenceVO | 9 | 0 |

| PricingForAdopterVO | 6 | 0 |

| ProgressCreateRequest | 14 | 0 |

| ProgressUpdateRequest | 12 | 0 |

| ProposalChapterItem | 5 | 0 |

| ProposalCourseItem | 7 | 0 |

| ProposalLeadCourseItem | 4 | 0 |

| ProposalSharedUnitItem | 7 | 0 |

| ProposalSignatureItem | 15 | 0 |

| ProposalTeamMemberItem | 10 | 0 |

| Question | 15 | 0 |

| QuestionChapter | 2 | 0 |

| QuestionCreateRequest | 12 | 0 |

| QuestionUpdateRequest | 11 | 0 |

| QuestionVO | 20 | 0 |

| R | 4 | 0 |

| RefreshRequest | 1 | 0 |

| RegisterRequest | 2 | 0 |

| RejectProposalRequest | 1 | 0 |

| RejectRequest | 1 | 0 |

| ReviewReport | 10 | 0 |

| ReviewReportActionRequest | 2 | 0 |

| ReviewReportVO | 13 | 0 |

| ScoreHistory | 9 | 0 |

| ScoreHistoryVO | 9 | 0 |

| SectionCreateRequest | 2 | 0 |

| SectionDTO | 21 | 0 |

| SectionUpdateRequest | 2 | 0 |

| SettingUpdateRequest | 3 | 0 |

| SlideUpdateRequest | 6 | 0 |

| StorageApplicationAutoSaveRequest | 1 | 0 |

| StorageApplicationPreviewVO | 37 | 0 |

| StorageApplicationSaveRequest | 35 | 0 |

| StorageApplicationSummaryVO | 10 | 0 |

| StorageApplicationVO | 42 | 0 |

| StudentActivityVO | 4 | 0 |

| StudentDetailVO | 7 | 0 |

| SubmitAnswerRequest | 7 | 0 |

| Tag | 5 | 0 |

| TagCreateRequest | 2 | 0 |

| TagUpdateRequest | 2 | 0 |

| TagVO | 4 | 0 |

| TeacherActionRequest | 3 | 0 |

| TeacherCourseVO | 6 | 0 |

| TeacherNotificationVO | 4 | 0 |

| TeacherRating | 12 | 0 |

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

| ToggleRegisterRequest | 1 | 0 |

| TrendPointVO | 2 | 0 |

| UpdateProfileRequest | 5 | 0 |

| UpdateStudentStatusRequest | 1 | 0 |

| UploadLimitRequest | 1 | 0 |

| UploadResultVO | 3 | 0 |

| User | 28 | 0 |

| UserApiKeyResponse | 3 | 0 |

| UserBatchImportDTO | 7 | 0 |

| UserCreateRequest | 17 | 0 |

| UserFollow | 4 | 0 |

| UserPageQuery | 10 | 0 |

| UserStatusRequest | 2 | 0 |

| UserTrendVO | 3 | 0 |

| UserUpdateRequest | 15 | 0 |

| UserVO | 27 | 0 |

| Video | 30 | 0 |

| VideoAnalyticsVO | 10 | 0 |

| VideoBookmark | 7 | 0 |

| VideoBookmarkCreateRequest | 3 | 0 |

| VideoBookmarkVO | 7 | 0 |

| VideoCreateRequest | 7 | 0 |

| VideoProgressReportRequest | 7 | 0 |

| VideoStatusVO | 5 | 0 |

| VideoUpdateRequest | 4 | 0 |

| VideoVO | 23 | 0 |

| WrongQuestion | 8 | 0 |

| WrongQuestionVO | 14 | 0 |


---

> 此契约为准入门禁基线。任何前端新字段必须匹配后端实体，否则 BLOCK。
