#!/bin/bash
# =============================================================================
# 清理 E2E/压测/调试遗留的测试数据
# =============================================================================
# 背景: 自动化测试 (Playwright/load-test) 会创建带特定前缀的测试数据:
#   - E2E-TEST-*   : 课程名/章节名
#   - PROMO-*      : 压测脚本创建
#   - CapTest-*    : 容量测试
#   - test_student / test_teacher / test_academic : 测试用户
#
# 这些数据会污染真实客户的"课程广场"和"我的课程",降低信任。
# 本脚本作为运维工具,可随时运行清理。
#
# 用法: bash scripts/cleanup-test-data.sh
# =============================================================================

set -e

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-micro_course}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-}

PSQL="psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME"

echo "=== 清理 E2E/压测测试数据 ==="
echo ""

# 1. 软删除测试课程
echo "[1/5] 软删除测试课程 (E2E-TEST-*, PROMO-*, CapTest-*)..."
PGPASSWORD="$DB_PASSWORD" $PSQL <<'EOF'
UPDATE courses
SET deleted_at = NOW(), status = 5, updated_at = NOW()
WHERE title ~ '^(PROMO|CapTest|E2E|Test)[-_]' 
  AND deleted_at IS NULL
RETURNING id, title;
EOF

# 2. 软删除这些课程的 enrollments
echo ""
echo "[2/5] 软删除测试课程的选课记录..."
PGPASSWORD="$DB_PASSWORD" $PSQL <<'EOF'
UPDATE enrollments
SET deleted_at = NOW()
WHERE course_id IN (SELECT id FROM courses WHERE deleted_at IS NOT NULL)
  AND deleted_at IS NULL
RETURNING id;
EOF

# 3. 软删除测试用户
echo ""
echo "[3/5] 软删除测试用户 (test_*)..."
PGPASSWORD="$DB_PASSWORD" $PSQL <<'EOF'
UPDATE users
SET deleted_at = NOW(), status = 0, updated_at = NOW()
WHERE username ~ '^test_(student|teacher|academic|user)' 
  AND deleted_at IS NULL
RETURNING id, username;
EOF

# 4. 软删除测试讨论
echo ""
echo "[4/5] 软删除测试讨论 (E2E Test, Test Post, TODO)..."
PGPASSWORD="$DB_PASSWORD" $PSQL <<'EOF'
UPDATE discussion_posts
SET deleted_at = NOW()
WHERE (title ~ '^(E2E Test|Test Post|TODO|DEBUG|fixme)' OR content ~ '^(E2E Test|Test Post|TODO|DEBUG|fixme)')
  AND deleted_at IS NULL
RETURNING id, title;
EOF

# 5. 报告
echo ""
echo "[5/5] 清理结果..."
PGPASSWORD="$DB_PASSWORD" $PSQL <<'EOF'
SELECT
  (SELECT COUNT(*) FROM courses WHERE deleted_at IS NOT NULL) AS courses_deleted,
  (SELECT COUNT(*) FROM enrollments WHERE deleted_at IS NOT NULL) AS enrollments_deleted,
  (SELECT COUNT(*) FROM users WHERE deleted_at IS NOT NULL) AS users_deleted,
  (SELECT COUNT(*) FROM discussion_posts WHERE deleted_at IS NOT NULL) AS discussions_deleted;
EOF

echo ""
echo "=== 清理完成 ==="
echo ""
echo "预防建议:"
echo "  1. 测试数据使用 'TEST-' 前缀,本脚本已覆盖"
echo "  2. Playwright e2e 测试加 test.afterAll 钩子清理"
echo "  3. 压测脚本结束必须 deleteById 压测数据"
echo "  4. 每周跑一次本脚本 (可加 crontab)"
