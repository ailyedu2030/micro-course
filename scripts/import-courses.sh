#!/bin/bash
# ==============================================================================
# scripts/import-courses.sh — 课程数据批量导入工具
# ==============================================================================
# 用途: 从 Excel 模板批量导入课程/章节/用户数据到微课平台
# 用法: bash scripts/import-courses.sh <excel-file.xlsx> [--dry-run] [--type=courses|users]
#
# 模板格式:
#   courses: 课程名称|教师工号|分类|学分|课时|简介
#   users:   工号|姓名|角色(student/teacher)|院系|邮箱
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()   { echo -e "${RED}[ERROR]${NC} $1"; }

if [ $# -lt 1 ]; then
  echo "用法: bash scripts/import-courses.sh <file.xlsx> [--dry-run] [--type=courses|users]"
  echo ""
  echo "选项:"
  echo "  --dry-run    仅验证数据，不实际导入"
  echo "  --type=courses  导入课程数据（默认）"
  echo "  --type=users    导入用户数据"
  exit 1
fi

INPUT_FILE="$1"
DRY_RUN=false
IMPORT_TYPE="courses"

for arg in "$@"; do
  case "$arg" in --dry-run) DRY_RUN=true ;; --type=*) IMPORT_TYPE="${arg#--type=}" ;; esac
done

if [ ! -f "$INPUT_FILE" ]; then
  err "文件不存在: $INPUT_FILE"
  exit 1
fi

echo ""
echo "╔════════════════════════════════════════╗"
echo "║  课程数据批量导入工具                   ║"
echo "╚════════════════════════════════════════╝"
echo ""
info "文件: $INPUT_FILE"
info "类型: $IMPORT_TYPE"
[ "$DRY_RUN" = true ] && warn "模式: 试运行（不实际写入）" || info "模式: 正式导入"
echo ""

# 检查 API 是否可用
API_BASE="${API_BASE:-http://localhost:8080}"
API_TOKEN="${API_TOKEN:-}"

if [ -z "$API_TOKEN" ]; then
  warn "未设置 API_TOKEN 环境变量"
  warn "请先登录获取 token:"
  echo "  API_TOKEN=\$(curl -s -X POST $API_BASE/api/auth/login \\"
  echo "    -H 'Content-Type: application/json' \\"
  echo "    -d '{\"username\":\"admin\",\"password\":\"admin123\"}' \\"
  echo "    | grep -o '\"accessToken\":\"[^\"]*\"' | cut -d'\"' -f4)"
  echo ""
  exit 1
fi

AUTH_HEADER="Authorization: Bearer $API_TOKEN"

# 检测系统命令
if command -v python3 &>/dev/null; then
  PYTHON=python3
elif command -v python &>/dev/null; then
  PYTHON=python
else
  err "需要 Python 3 来处理 Excel 文件"
  exit 1
fi

# 临时 Python 导入脚本
TMP_PY=$(mktemp /tmp/import-courses-XXXXXX.py)

if [ "$IMPORT_TYPE" = "courses" ]; then
  cat > "$TMP_PY" << 'PYEOF'
import sys, json, urllib.request, os

try:
    import openpyxl
except ImportError:
    print("ERROR: 需要安装 openpyxl: pip install openpyxl")
    sys.exit(1)

filepath = sys.argv[1]
dry_run = sys.argv[2] == 'true'
api_base = os.environ.get('API_BASE', 'http://localhost:8080')
api_token = os.environ.get('API_TOKEN', '')

wb = openpyxl.load_workbook(filepath, read_only=True)
ws = wb.active

headers = [cell.value for cell in next(ws.iter_rows())]
print(f"表头: {headers}")
print(f"数据行数: {ws.max_row - 1}")

total = 0
errors = 0

for row_idx, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
    if all(cell is None for cell in row):
        continue
    total += 1
    record = dict(zip(headers, row))

    course_name = record.get('课程名称') or record.get('name') or ''
    teacher_id = record.get('教师工号') or record.get('teacher_id') or ''
    category = record.get('分类') or record.get('category') or ''
    credits = record.get('学分') or record.get('credits') or 0
    hours = record.get('课时') or record.get('hours') or 0
    description = record.get('简介') or record.get('description') or ''

    if not course_name:
        print(f"  [跳过] 第{row_idx}行: 缺少课程名称")
        errors += 1
        continue

    payload = {
        "title": course_name,
        "teacherId": teacher_id if teacher_id else None,
        "categoryName": category,
        "credits": float(credits) if credits else 0,
        "hours": int(hours) if hours else 0,
        "description": description,
        "status": 1
    }

    if dry_run:
        print(f"  [验证] {course_name} — 教师:{teacher_id} 学分:{credits}")
    else:
        try:
            req = urllib.request.Request(
                f"{api_base}/api/admin/courses/import",
                data=json.dumps(payload).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Authorization': f'Bearer {api_token}'
                },
                method='POST'
            )
            resp = urllib.request.urlopen(req)
            result = json.loads(resp.read())
            if result.get('code') == 200:
                print(f"  [OK]    {course_name} — 导入成功")
            else:
                print(f"  [失败]  {course_name} — {result.get('message', '未知错误')}")
                errors += 1
        except urllib.error.HTTPError as e:
            body = e.read().decode()
            print(f"  [失败]  {course_name} — HTTP {e.code}: {body[:100]}")
            errors += 1
        except Exception as e:
            print(f"  [失败]  {course_name} — {str(e)[:100]}")
            errors += 1

print(f"\n总计: {total} 条, 成功: {total - errors}, 失败: {errors}")
PYEOF

elif [ "$IMPORT_TYPE" = "users" ]; then
  cat > "$TMP_PY" << 'PYEOF'
import sys, json, urllib.request, os

try:
    import openpyxl
except ImportError:
    print("ERROR: 需要安装 openpyxl: pip install openpyxl")
    sys.exit(1)

filepath = sys.argv[1]
dry_run = sys.argv[2] == 'true'
api_base = os.environ.get('API_BASE', 'http://localhost:8080')
api_token = os.environ.get('API_TOKEN', '')

wb = openpyxl.load_workbook(filepath, read_only=True)
ws = wb.active

headers = [cell.value for cell in next(ws.iter_rows())]
print(f"表头: {headers}")
print(f"数据行数: {ws.max_row - 1}")

total = 0
errors = 0

for row_idx, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
    if all(cell is None for cell in row):
        continue
    total += 1
    record = dict(zip(headers, row))

    username = str(record.get('工号') or record.get('username') or '')
    name = record.get('姓名') or record.get('name') or ''
    role = record.get('角色') or record.get('role') or 'student'
    department = record.get('院系') or record.get('department') or ''
    email = record.get('邮箱') or record.get('email') or ''

    if not username or not name:
        print(f"  [跳过] 第{row_idx}行: 缺少工号或姓名")
        errors += 1
        continue

    role = role.lower()
    if role not in ('student', 'teacher', 'admin', 'academic'):
        print(f"  [跳过] 第{row_idx}行: 无效角色 '{role}'")
        errors += 1
        continue

    payload = {
        "username": username,
        "name": name,
        "role": role.upper(),
        "departmentName": department,
        "email": email if email else f"{username}@school.edu.cn",
        "password": "school123"
    }

    if dry_run:
        print(f"  [验证] {name}({username}) — {role} — {department}")
    else:
        try:
            req = urllib.request.Request(
                f"{api_base}/api/admin/users/import",
                data=json.dumps(payload).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Authorization': f'Bearer {api_token}'
                },
                method='POST'
            )
            resp = urllib.request.urlopen(req)
            result = json.loads(resp.read())
            if result.get('code') == 200:
                print(f"  [OK]    {name}({username}) — 导入成功")
            else:
                print(f"  [失败]  {name}({username}) — {result.get('message', '未知错误')}")
                errors += 1
        except urllib.error.HTTPError as e:
            body = e.read().decode()
            print(f"  [失败]  {name}({username}) — HTTP {e.code}: {body[:100]}")
            errors += 1
        except Exception as e:
            print(f"  [失败]  {name}({username}) — {str(e)[:100]}")
            errors += 1

print(f"\n总计: {total} 条, 成功: {total - errors}, 失败: {errors}")
PYEOF
fi

$PYTHON "$TMP_PY" "$INPUT_FILE" "$DRY_RUN"
PY_EXIT=$?
rm -f "$TMP_PY"

exit $PY_EXIT
