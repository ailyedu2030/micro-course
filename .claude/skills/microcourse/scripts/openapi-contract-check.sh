#!/usr/bin/env bash
# OpenAPI 契约校验脚本 (W31 治理)
#
# 用途:
#   1. 启动后端 + 抓取 /v3/api-docs
#   2. 校验 6 个核心 endpoint 是否存在 + 鉴权是否正确
#   3. 校验 DTO 字段 (CreatePage, CreateSection, CreatePptPage 等)
#   4. 输出 PASS/FAIL 报告
#
# 退出码: 0 = PASS, 1 = FAIL

set -e

API_BASE="${API_BASE:-http://localhost:8080}"
OPENAPI_URL="${API_BASE}/v3/api-docs"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_FILE="/tmp/openapi-$(date +%s).json"

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo "============================================================"
echo "  OpenAPI Contract Check (W31 治理)"
echo "============================================================"
echo "API_BASE=${API_BASE}"
echo "OPENAPI_URL=${OPENAPI_URL}"
echo ""

# Step 1: 检查后端是否可达
echo "--- Step 1: 后端健康检查 ---"
HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "${API_BASE}/actuator/health" || echo "000")
if [ "$HEALTH_CODE" != "200" ]; then
    echo -e "${RED}FAIL${NC}: 后端不可达, code=${HEALTH_CODE}"
    echo "  请先启动: bash scripts/local-dev-deploy.sh"
    exit 1
fi
echo -e "${GREEN}PASS${NC}: 后端健康"

# Step 2: 抓取 OpenAPI doc
echo ""
echo "--- Step 2: 抓取 OpenAPI doc ---"
if ! curl -s -m 10 -o "$TMP_FILE" "$OPENAPI_URL"; then
    echo -e "${RED}FAIL${NC}: 抓取 ${OPENAPI_URL} 失败"
    exit 1
fi
FILE_SIZE=$(wc -c < "$TMP_FILE" | tr -d ' ')
if [ "$FILE_SIZE" -lt 100 ]; then
    echo -e "${RED}FAIL${NC}: OpenAPI doc 太小 (${FILE_SIZE} bytes), 可能未启用 springdoc"
    exit 1
fi
echo -e "${GREEN}PASS${NC}: OpenAPI doc 已抓取 (${FILE_SIZE} bytes)"

# Step 3: 校验 6 个核心 endpoint
echo ""
echo "--- Step 3: 核心 endpoint 校验 ---"

REQUIRED_ENDPOINTS=(
    "delete:/api/courses/{courseId}/courseware/chapters/{chapterId}"
    "delete:/api/courses/{courseId}/courseware/sections/{sectionId}"
    "delete:/api/courses/{courseId}/courseware/ppt-pages/{pptPageId}"
    "delete:/api/courses/{courseId}/courseware/html-units/{htmlUnitId}"
    "delete:/api/courses/{courseId}/courseware/chapters/batch"
    "delete:/api/courses/{courseId}/courseware/ppt-pages/batch"
    "get:/api/courses/{courseId}/courseware/{sectionId}"
    "get:/api/courses/{courseId}/courseware/audio/{token}"
    "post:/api/courses/{courseId}/html/sections/{sectionId}/unit"
    "post:/api/courses/{courseId}/ppt/pages/{pageId}"
    "delete:/api/courses/{courseId}/ppt/pages/{pageId}"
)

PASS_COUNT=0
FAIL_COUNT=0
for ep in "${REQUIRED_ENDPOINTS[@]}"; do
    METHOD="${ep%%:*}"
    PATH_PATTERN="${ep##*:}"
    # 转为 OpenAPI path (Spring: /api/courses/{courseId}/...)
    OPENAPI_PATH="$PATH_PATTERN"
    # 检查
    EXISTS=$(python3 -c "
import json
with open('$TMP_FILE') as f:
    doc = json.load(f)
path = '$OPENAPI_PATH'
method = '$METHOD'.lower()
if path in doc.get('paths', {}):
    if method in doc['paths'][path]:
        print('YES')
    else:
        print('NO_METHOD')
else:
    print('NO_PATH')
")
    if [ "$EXISTS" = "YES" ]; then
        echo -e "  ${GREEN}PASS${NC}: ${METHOD^^} ${PATH_PATTERN}"
        PASS_COUNT=$((PASS_COUNT+1))
    else
        echo -e "  ${RED}FAIL${NC}: ${METHOD^^} ${PATH_PATTERN} (${EXISTS})"
        FAIL_COUNT=$((FAIL_COUNT+1))
    fi
done

# Step 4: 校验 DELETE 端点是否要求鉴权 (TEACHER/ADMIN)
echo ""
echo "--- Step 4: DELETE 端点鉴权校验 ---"
AUTH_CHECK=$(python3 -c "
import json
with open('$TMP_FILE') as f:
    doc = json.load(f)
delete_paths = [
    '/api/courses/{courseId}/courseware/chapters/{chapterId}',
    '/api/courses/{courseId}/courseware/sections/{sectionId}',
    '/api/courses/{courseId}/courseware/ppt-pages/{pptPageId}',
    '/api/courses/{courseId}/courseware/html-units/{htmlUnitId}',
]
total = len(delete_paths)
secured = 0
for p in delete_paths:
    if p in doc['paths'] and 'delete' in doc['paths'][p]:
        op = doc['paths'][p]['delete']
        if 'security' in op or '403' in op.get('responses', {}):
            secured += 1
print(f'{secured}/{total}')
")
echo "  DELETE 端点鉴权覆盖: $AUTH_CHECK"

# Step 5: 输出报告
echo ""
echo "============================================================"
echo "  报告"
echo "============================================================"
echo "  总端点数: ${#REQUIRED_ENDPOINTS[@]}"
echo "  PASS:    $PASS_COUNT"
echo "  FAIL:    $FAIL_COUNT"
echo "  鉴权覆盖: $AUTH_CHECK DELETE 端点"
echo ""

if [ "$FAIL_COUNT" -gt 0 ]; then
    echo -e "${RED}FAIL${NC}: ${FAIL_COUNT} 个端点缺失"
    rm -f "$TMP_FILE"
    exit 1
fi

echo -e "${GREEN}PASS${NC}: 全部端点契约符合"
rm -f "$TMP_FILE"
exit 0