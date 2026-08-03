#!/bin/bash
# 微课平台 · DB 一致性 + 状态机 + 并发校验 (server-side 脚本)
# 运行: bash scripts/qa-db-consistency.sh
# 输出: /Users/jackie/微课平台/.qa-results/qa-db-consistency-<ts>.json

set -uo pipefail

RESULTS_FILE="/Users/jackie/微课平台/.qa-results/qa-db-consistency-$(date +%s).json"
PG_CONTAINER="microcourse-pg-test"
PG_USER="postgres"
PG_DB="micro_course_test"

mkdir -p /Users/jackie/微课平台/.qa-results

results="["
first=1

record() {
  local name="$1"
  local ok="$2"
  local details="$3"
  if [ $first -eq 0 ]; then results+=","; fi
  first=0
  results+="{\"name\":\"$name\",\"ok\":$ok,\"details\":$(echo "$details" | python3 -c 'import json,sys;print(json.dumps(sys.stdin.read().rstrip()))')}"
}

# DB01 课程状态机一致性: 课程 status 必须为 0-6 枚举值
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM courses WHERE status NOT IN (0,1,2,3,4,5,6);" 2>/dev/null)
record "DB01-课程status枚举" "$([ "$out" = "0" ] && echo true || echo false)" "{\"invalidCount\":$out}"

# DB02 微专业状态机一致性
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM micro_specialties WHERE status NOT IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','RECRUITING','COMPLETED','CANCELLED','ARCHIVED');" 2>/dev/null)
record "DB02-微专业status枚举" "$([ "$out" = "0" ] && echo true || echo false)" "{\"invalidCount\":$out}"

# DB03 选课状态机一致性
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM enrollments WHERE enrollment_status NOT IN ('PENDING','APPROVED','REJECTED','WAITLIST','CANCELLED','SUSPENDED','DROPPED','COMPLETED');" 2>/dev/null)
record "DB03-选课status枚举" "$([ "$out" = "0" ] && echo true || echo false)" "{\"invalidCount\":$out}"

# DB04 user.status 一致性 (0=INACTIVE, 1=ACTIVE, 2=DISABLED, 3=DELETED)
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM users WHERE status NOT IN (0,1,2,3);" 2>/dev/null)
record "DB04-用户status枚举" "$([ "$out" = "0" ] && echo true || echo false)" "{\"invalidCount\":$out}"

# DB05 用户唯一约束: username 应唯一
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM (SELECT username FROM users WHERE deleted_at IS NULL GROUP BY username HAVING COUNT(*) > 1) t;" 2>/dev/null)
record "DB05-用户username唯一" "$([ "$out" = "0" ] && echo true || echo false)" "{\"duplicates\":$out}"

# DB06 软删除用户不应有 in-use 关联 (enrollments/favorites/操作日志引用 deleted user)
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM enrollments e JOIN users u ON e.user_id = u.id WHERE u.status = 3 AND u.deleted_at IS NOT NULL;" 2>/dev/null)
record "DB06-软删除用户无活动选课" "$([ "$out" = "0" ] && echo true || echo false)" "{\"danglingEnrollments\":$out}"

# DB07 课程章节课时一致性: 每个 section 应有 chapter_id 指向存在的 chapter
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM course_sections s LEFT JOIN course_chapters c ON s.chapter_id = c.id WHERE c.id IS NULL;" 2>/dev/null)
record "DB07-课时-章节引用完整" "$([ "$out" = "0" ] && echo true || echo false)" "{\"danglingSections\":$out}"

# DB08 微专业课程引用完整
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM micro_specialty_courses msc LEFT JOIN micro_specialties ms ON msc.micro_specialty_id = ms.id WHERE ms.id IS NULL;" 2>/dev/null)
record "DB08-微专业-课程引用完整" "$([ "$out" = "0" ] && echo true || echo false)" "{\"dangling\":$out}"

# DB09 微专业教师引用完整
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM micro_specialty_teachers mst LEFT JOIN micro_specialties ms ON mst.micro_specialty_id = ms.id WHERE ms.id IS NULL;" 2>/dev/null)
record "DB09-微专业-教师引用完整" "$([ "$out" = "0" ] && echo true || echo false)" "{\"dangling\":$out}"

# DB10 操作日志完整性 (任意 select)
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM operation_logs;" 2>/dev/null)
record "DB10-操作日志表可查询" "$([ -n "$out" ] && echo true || echo false)" "{\"count\":$out}"

# DB11 索引完整性
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public';" 2>/dev/null)
record "DB11-索引数" "$([ "$out" -gt 50 ] && echo true || echo false)" "{\"indexCount\":$out}"

# DB12 Flyway 迁移历史无失败
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;" 2>/dev/null)
record "DB12-Flyway迁移全成功" "$([ "$out" = "0" ] && echo true || echo false)" "{\"failedMigrations\":$out}"

# DB13 课程发布数 (非PUBLISHED status 不应被学生选课 — 与DB-04B01 一致)
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM enrollments e JOIN courses c ON e.course_id = c.id WHERE c.status NOT IN (2,4);" 2>/dev/null)
record "DB13-选课-课程必须已发布" "$([ "$out" = "0" ] && echo true || echo false)" "{\"invalidEnrollments\":$out}"

# DB14 微专业修读引用完整
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM micro_specialty_enrollments mse LEFT JOIN micro_specialties ms ON mse.micro_specialty_id = ms.id WHERE ms.id IS NULL;" 2>/dev/null)
record "DB14-微专业修读-引用完整" "$([ "$out" = "0" ] && echo true || echo false)" "{\"dangling\":$out}"

# DB15 评价关联性
out=$(docker exec $PG_CONTAINER psql -U $PG_USER -d $PG_DB -tA -c "SELECT COUNT(*) FROM course_reviews r LEFT JOIN courses c ON r.course_id = c.id WHERE c.id IS NULL;" 2>/dev/null)
record "DB15-评价-课程引用完整" "$([ "$out" = "0" ] && echo true || echo false)" "{\"dangling\":$out}"

results+="]"
echo "$results" | python3 -c "
import json, sys
data = json.load(sys.stdin)
total = len(data)
failed = sum(1 for r in data if not r['ok'])
print(f'DB CONSISTENCY: failed={failed}/{total}')
for r in data:
    print(('✓' if r['ok'] else '✗') + ' ' + r['name'] + ' ' + json.dumps(r['details']))
print()
print('SAVED ' + '$RESULTS_FILE')
import os
os.makedirs(os.path.dirname('$RESULTS_FILE'), exist_ok=True)
with open('$RESULTS_FILE', 'w') as f:
    json.dump({'ts': __import__('datetime').datetime.now().isoformat(), 'total': total, 'failed': failed, 'results': data}, f, indent=2)
"
