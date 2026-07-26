# 讨论/评价域 Spec 漂移全量修复

## 修复项 (6 项)
1. discussion_comment_likes 表 → 数据字典补充 (M)
2. course_review_logs SMALLINT→Integer 类型同步 (D)
3. DiscussionPost 状态机 Integer→枚举化 (类似 UserStatus 模式)
4. CourseReview 状态机 Integer→枚举化
5. 数据字典 discussion_comments.is_anonymous 补充 (V46)
6. API 契约同步 @PreAuthorize + 状态机

## 方法论
git blame 验证: discussion domain 由 总工程师 在 6cdb0db6 创建
与 UserStatus 相同的 Integer→枚举模式修复
