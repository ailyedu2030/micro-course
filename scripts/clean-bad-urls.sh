#!/bin/bash
# 清理测试数据中的无效 URL (避免 P1 UI 渲染失败)
# 失效 URL:
#   - http://x.com/*, http://example.com/* (loadtest 注入)
#   - https://example.com/* (E2E 测试)
#   - 其他非 /api/files/* 本地路径
#
# 用法: bash scripts/clean-bad-urls.sh
set -e
# 从 micro-course-api/.env 读 DB 配置,支持注释和空格
DB_HOST=$(grep '^DB_HOST=' micro-course-api/.env | cut -d= -f2- | tr -d '"' | tr -d "'" | xargs)
DB_PORT=$(grep '^DB_PORT=' micro-course-api/.env | cut -d= -f2- | tr -d '"' | tr -d "'" | xargs)
DB_NAME=$(grep '^DB_NAME=' micro-course-api/.env | cut -d= -f2- | tr -d '"' | tr -d "'" | xargs)
DB_USERNAME=$(grep '^DB_USERNAME=' micro-course-api/.env | cut -d= -f2- | tr -d '"' | tr -d "'" | xargs)
DB_PASSWORD=$(grep '^DB_PASSWORD=' micro-course-api/.env | cut -d= -f2- | tr -d '"' | tr -d "'" | xargs)
[ -z "$DB_PORT" ] && DB_PORT=5432
export PGPASSWORD="$DB_PASSWORD"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<SQL
-- 统计
SELECT 'cover_url' AS field, COUNT(*) AS bad_count
  FROM courses
  WHERE cover_url IS NOT NULL AND cover_url NOT LIKE '/api/files/%'
UNION ALL
SELECT 'avatar_url', COUNT(*)
  FROM users
  WHERE avatar_url IS NOT NULL AND avatar_url NOT LIKE '/api/files/%' AND avatar_url NOT LIKE 'data:%';
SQL
echo ""
echo "执行清理? (y/N)"
read -r ans
if [ "$ans" = "y" ]; then
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<SQL
UPDATE courses SET cover_url = NULL
  WHERE cover_url IS NOT NULL AND cover_url NOT LIKE '/api/files/%';
UPDATE users SET avatar_url = NULL
  WHERE avatar_url IS NOT NULL AND avatar_url NOT LIKE '/api/files/%' AND avatar_url NOT LIKE 'data:%';
SQL
  echo "✓ 已清理,前端 coverHelper.js 会用类别化 SVG 兜底"
fi
