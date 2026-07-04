-- V137: 为所有教师授予互动课件插件权限
-- 使 TEACHER 角色用户可创建 INTERACTIVE 类型的课程
-- 对应 CourseAdminServiceImpl.checkPluginGrant() 的 PLUGIN_NO_GRANT 检查

INSERT INTO plugin_grants (plugin_id, grant_type, grantee_id, created_at)
SELECT 'interactive', 'TEACHER', id, NOW()
FROM users
WHERE role = 'TEACHER'
  AND deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plugin_grants pg
    WHERE pg.plugin_id = 'interactive'
      AND pg.grant_type = 'TEACHER'
      AND pg.grantee_id = users.id
  );
