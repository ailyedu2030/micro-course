#!/bin/bash
# =============================================================================
# 隔离 e2e 环境课程夹具种子
# 供 local-dev-deploy.sh 在 Playwright 前调用：
#   - 课程 1 (VIDEO, 归属 p0_teacher) → XSS PartA/B/C 上传与播放器
#   - 课程 133 (INTERACTIVE) → phase11 互动课程用例（spec 硬编码 /courses/133）
#   - 章节 + HTML 课件 + student(7) 选课
# 注意：本脚本面向本地隔离测试库，绝不面向生产。
# =============================================================================
set -euo pipefail

API_BASE="${1:-http://localhost:8089}"
DB_CONTAINER="${2:-microcourse-pg-test}"
DB_USER="${3:-postgres}"
DB_NAME="${4:-micro_course_test}"

json_token() { python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])"; }

AT=$(curl -s -X POST "$API_BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"teacher1","password":"password123"}' | json_token)
P0=$(curl -s -X POST "$API_BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"p0_teacher","password":"student123"}' | json_token)
ADMIN_AT=$(curl -s -X POST "$API_BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | json_token)
ST=$(curl -s -X POST "$API_BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"student","password":"student123"}' | json_token)

PSQL() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "$1"; }
PSQL_T() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "$1" | tr -d ' \n'; }

# 1. 分类（admin）
CAT=$(PSQL_T "SELECT id FROM course_categories ORDER BY id LIMIT 1;")
if [ -z "$CAT" ]; then
  CAT=$(curl -s -X POST "$API_BASE/api/course-categories" -H "Authorization: Bearer $ADMIN_AT" -H 'Content-Type: application/json' \
    -d '{"name":"E2E测试分类","level":1,"sortOrder":1}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
fi

# 2. 课程 1（VIDEO，p0_teacher 创建即归属 id=6，供 XSS 用例以 p0_teacher 上传）
CID1=$(curl -s -X POST "$API_BASE/api/courses" -H "Authorization: Bearer $P0" -H 'Content-Type: application/json' \
  -d "{\"title\":\"E2E测试课程1\",\"courseType\":\"VIDEO\",\"categoryId\":$CAT,\"offerDepartmentId\":1,\"difficulty\":1,\"description\":\"e2e fixture\",\"price\":0}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

# 3. 课程 133（INTERACTIVE 需 ADMIN；强制 id=133 匹配 phase11 硬编码）
CID133=$(curl -s -X POST "$API_BASE/api/courses" -H "Authorization: Bearer $ADMIN_AT" -H 'Content-Type: application/json' \
  -d "{\"title\":\"E2E互动课程133\",\"courseType\":\"INTERACTIVE\",\"categoryId\":$CAT,\"offerDepartmentId\":1,\"difficulty\":1,\"description\":\"e2e fixture\",\"price\":0,\"teacherId\":2}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
if [ "$CID133" != "133" ]; then
  PSQL "UPDATE courses SET id=133 WHERE id=$CID133;"
  PSQL "SELECT setval('courses_id_seq', GREATEST((SELECT MAX(id) FROM courses), 1));"
fi

# 4. 发布两门课程
PSQL "UPDATE courses SET status=2, pricing_status='APPROVED' WHERE id IN ($CID1, 133);"

# 5. 章节
CH1=$(curl -s -X POST "$API_BASE/api/courses/$CID1/chapters" -H "Authorization: Bearer $P0" -H 'Content-Type: application/json' \
  -d '{"title":"第一章","sectionType":"VIDEO","sortOrder":1}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
CH133=$(curl -s -X POST "$API_BASE/api/courses/133/chapters" -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d '{"title":"第一章","sectionType":"INTERACTIVE","sortOrder":1}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

# 6. HTML 课件（ADMIN 绕过灰度白名单；SLIDES_HTML_WHITELIST 已含 6 双保险）
printf '<!DOCTYPE html><html><body><p>e2e fixture slide</p></body></html>' > /tmp/e2e-fixture.html
curl -s -X POST "$API_BASE/api/courses/$CID1/slides/upload" -H "Authorization: Bearer $ADMIN_AT" \
  -F "file=@/tmp/e2e-fixture.html;type=text/html" -F "chapterId=$CH1" >/dev/null
curl -s -X POST "$API_BASE/api/courses/133/slides/upload" -H "Authorization: Bearer $ADMIN_AT" \
  -F "file=@/tmp/e2e-fixture.html;type=text/html" -F "chapterId=$CH133" >/dev/null

# 7. student(7) 选课 1 与 133
curl -s -X POST "$API_BASE/api/enrollments" -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' -d "{\"courseId\":$CID1}" >/dev/null
curl -s -X POST "$API_BASE/api/enrollments" -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' -d '{"courseId":133}' >/dev/null

echo "e2e fixtures ready: course1=$CID1(owner p0_teacher) course133=133 slides+enrollments OK"
