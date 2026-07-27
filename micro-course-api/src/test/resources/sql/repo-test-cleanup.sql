-- 清理 Repository 测试可能冲突的存量数据
-- 原因：@Transactional 只能回滚当前 JVM 内的数据，跨 JVM 运行（reuseForks=false）的
-- 历史测试失败会留下已提交的脏数据，导致 ON CONFLICT DO NOTHING 静默跳过后继插入。
DELETE FROM learning_progress WHERE user_id IN (6, 7) AND course_id IN (1, 2);
DELETE FROM enrollments   WHERE user_id IN (6, 7) AND course_id IN (1, 2, 3, 4);
DELETE FROM orders        WHERE user_id = 7 AND course_id = 1;
DELETE FROM users         WHERE username LIKE 'test_%';
