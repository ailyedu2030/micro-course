#!/bin/bash
# ===================================================================
# convergence-check-status-machine.sh — 状态机文档双向校验（P3-7）
#
# 目的：自动检测"文档与代码脱节"——比对 Java 枚举常量值
#       与 docs/数据字典.md 中登记的取值集合，双向报告差异。
#
# 设计原则：
#   - 仅警告，绝不失败（始终 exit 0），避免立即把 CI 挂红；
#     待后续 Phase 收紧为 CI 必过门禁（见 README）。
#   - 防御性跳过尚未实现的枚举文件（如 UserStatus / OrderStatus）。
#
# 用法: bash tools/convergence-check-status-machine.sh
# ===================================================================

# 不使用 set -e：grep 无匹配返回 1 属正常情形，不应中断校验流程。
set +e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT" || exit 0

DICT="docs/数据字典.md"
ENUM_DIR="micro-course-api/src/main/java/com/microcourse/enums"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

WARN_COUNT=0
SKIP_COUNT=0
OK_COUNT=0

echo "========================================"
echo "  状态机文档双向校验 (P3-7)"
echo "  目标：枚举值代码 vs $DICT"
echo "========================================"
echo ""

if [ ! -f "$DICT" ]; then
    echo -e "${YELLOW}⚠️  数据字典不存在：$DICT —— 跳过全部校验${NC}"
    echo ""
    echo "=== 校验完成（数据字典缺失）==="
    exit 0
fi

# ---- 提取 Java 枚举常量名 ----
# 仅匹配"行首缩进 + 全大写标识符 + 左括号"的枚举常量声明；
# 全大写约束天然排除构造器(PascalCase)、方法(camelCase)与
# static final 常量(无紧邻左括号)。
extract_java_enum() {
    local f="$1"
    grep -oE '^[[:space:]]+[A-Z][A-Z0-9_]*[[:space:]]*\(' "$f" 2>/dev/null \
        | grep -oE '[A-Z][A-Z0-9_]*' \
        | sort -u
}

# ---- 从数据字典锚点行提取登记的枚举取值 ----
# 提取行内长度≥3 的大写标识符，并过滤 DB 类型 / 约束等噪音词。
extract_doc_enum() {
    local anchor="$1"
    grep -F "$anchor" "$DICT" 2>/dev/null \
        | grep -oE '[A-Z][A-Z0-9_]{2,}' \
        | grep -vxE 'NULL|NOT|DEFAULT|VARCHAR|BIGINT|BIGSERIAL|DECIMAL|TIMESTAMP|INTEGER|BOOLEAN|TEXT|UNIQUE|SITE|EMAIL|WECHAT|ALL|NOW|PK|FK' \
        | sort -u
}

# ---- 单个枚举校验 ----
# 参数: 枚举名 | Java文件名 | 数据字典锚点
check_enum() {
    local name="$1"
    local java_file="$2"
    local anchor="$3"
    local path="$ENUM_DIR/$java_file"

    echo "--- $name ---"

    if [ ! -f "$path" ]; then
        printf "  ${YELLOW}SKIP${NC}: 枚举文件未实现 [%s] —— 待后续 Phase 补齐\n" "$path"
        SKIP_COUNT=$((SKIP_COUNT + 1))
        echo ""
        return
    fi

    local java_vals doc_vals
    java_vals="$(extract_java_enum "$path")"
    doc_vals="$(extract_doc_enum "$anchor")"

    echo "  Java 枚举: $(echo "$java_vals" | tr '\n' ' ')"
    echo "  Doc  枚举: $(echo "$doc_vals" | tr '\n' ' ')"

    if [ -z "$doc_vals" ]; then
        echo -e "  ${YELLOW}⚠️  数据字典未登记取值集合（锚点：$anchor）${NC}"
        WARN_COUNT=$((WARN_COUNT + 1))
        echo ""
        return
    fi

    local missing_in_doc missing_in_java
    missing_in_doc="$(comm -23 <(printf '%s\n' "$java_vals") <(printf '%s\n' "$doc_vals") | grep -v '^$')"
    missing_in_java="$(comm -13 <(printf '%s\n' "$java_vals") <(printf '%s\n' "$doc_vals") | grep -v '^$')"

    if [ -z "$missing_in_doc" ] && [ -z "$missing_in_java" ]; then
        echo -e "  ${GREEN}✓ 一致${NC}"
        OK_COUNT=$((OK_COUNT + 1))
    else
        if [ -n "$missing_in_doc" ]; then
            echo -e "  ${YELLOW}⚠️  Java 有但 Doc 缺失：$(echo "$missing_in_doc" | tr '\n' ' ')${NC}"
            WARN_COUNT=$((WARN_COUNT + 1))
        fi
        if [ -n "$missing_in_java" ]; then
            echo -e "  ${YELLOW}⚠️  Doc 有但 Java 缺失：$(echo "$missing_in_java" | tr '\n' ' ')${NC}"
            WARN_COUNT=$((WARN_COUNT + 1))
        fi
    fi
    echo ""
}

# ---- 枚举清单（数据驱动）----
# 已实现：EnrollmentStatus / CourseStatus / NotificationType
# 待实现（自动 SKIP）：UserStatus / TeachingClassStatus / OrderStatus
check_enum "EnrollmentStatus"     "EnrollmentStatus.java"     "选课状态："
check_enum "CourseStatus"         "CourseStatus.java"         "0=DRAFT"
check_enum "NotificationType"     "NotificationType.java"     "消息类型（"
check_enum "UserStatus"           "UserStatus.java"           "用户状态"
check_enum "TeachingClassStatus"  "TeachingClassStatus.java"  "教学班状态"
check_enum "OrderStatus"          "OrderStatus.java"          "PENDING / PAID / CANCELLED"

# ---- 汇总 ----
echo "========================================"
echo -e "  结果：${GREEN}一致 $OK_COUNT${NC} | ${YELLOW}警告 $WARN_COUNT${NC} | SKIP $SKIP_COUNT"
echo "========================================"
echo "说明：本脚本仅警告不失败（始终退出 0）。"
echo "      建议后续 Phase 同步数据字典后，再收紧为 CI 必过门禁。"

# 始终成功退出，不阻塞 CI。
exit 0
