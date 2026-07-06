#!/bin/bash
# ============================================================================
# precheck-extra.sh · 微课平台扩展预检 (课程管理域 v4.0 增强)
# ----------------------------------------------------------------------------
# 依据：course-domain-drift-fix 模式 3 防御措施
#   - §3.4.1 禁止状态字段硬编码
#   - §3.4.2 禁止 Controller 含 SecurityUtil.hasRole
#   - §3.4.3 禁止 Controller 含文件魔数校验
#   - §3.4.6 禁止绕过专用端点
#
# 用法:
#   bash scripts/precheck-extra.sh
#
# 退出码:
#   0 = PASS
#   1 = FAIL (有违规)
# ============================================================================

set -u

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

FAIL=0
WARN=0

echo -e "${YELLOW}=== 微课平台扩展预检 ===${NC}"
echo ""

# ----------------------------------------------------------------------------
# 规则 1: 禁止状态字段硬编码 (V1/V7 防御)
# 检测: .eq(Entity::getStatus, 数字字面量) 或 .eq(.*Status, 数字)
# 排除: 注释行 / 状态机转换配置
# ----------------------------------------------------------------------------
echo -n "[规则1] 禁止状态字段硬编码... "
HITS=$(grep -rn '\.eq(.*[Ss]tatus,\s*[0-9]' micro-course-api/src/main/java/ 2>/dev/null \
    | grep -v "/\*\|\* \|//\|\.canTransitionTo\|CourseStatus\.\|\.getCode()\|\.setStatus(" \
    | wc -l | tr -d ' ')
if [ "$HITS" -gt 0 ]; then
    echo -e "${RED}FAIL ($HITS 处)${NC}"
    grep -rn '\.eq(.*[Ss]tatus,\s*[0-9]' micro-course-api/src/main/java/ 2>/dev/null \
        | grep -v "/\*\|\* \|//\|CourseStatus\." | head -5
    FAIL=1
else
    echo -e "${GREEN}PASS${NC}"
fi

# ----------------------------------------------------------------------------
# 规则 2: 禁止 Controller 含 SecurityUtil.hasRole
# ----------------------------------------------------------------------------
echo -n "[规则2] 禁止 Controller 含 SecurityUtil.hasRole... "
HITS=$(grep -rn "SecurityUtil\.hasRole" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null \
    | grep -v "import " | wc -l | tr -d ' ')
if [ "$HITS" -gt 0 ]; then
    echo -e "${RED}FAIL ($HITS 处)${NC}"
    grep -rn "SecurityUtil\.hasRole" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null \
        | grep -v "import " | head -5
    FAIL=1
else
    echo -e "${GREEN}PASS${NC}"
fi

# ----------------------------------------------------------------------------
# 规则 3: 禁止 Controller 直接读 InputStream 验魔数
# 检测: Controller 中调用 InputStream / getInputStream + magic bytes 比较
# ----------------------------------------------------------------------------
echo -n "[规则3] 禁止 Controller 文件魔数校验... "
HITS=$(grep -rn "getInputStream" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null \
    | wc -l | tr -d ' ')
if [ "$HITS" -gt 0 ]; then
    echo -e "${RED}FAIL ($HITS 处)${NC}"
    grep -rn "getInputStream" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null | head -5
    FAIL=1
else
    echo -e "${GREEN}PASS${NC}"
fi

# ----------------------------------------------------------------------------
# 规则 4: 禁止 Controller 含私有静态工具方法 (V6 防御)
# 检测: Controller 文件中出现 private static 方法定义
# ----------------------------------------------------------------------------
echo -n "[规则4] 禁止 Controller 含私有静态工具方法... "
HITS=$(grep -rn "private static" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null \
    | grep -v "private static final Logger\|private static final String\|private static final long\|private static final int\|private static final Integer\|private static final Boolean" \
    | grep -v "import " | wc -l | tr -d ' ')
if [ "$HITS" -gt 0 ]; then
    echo -e "${YELLOW}WARN ($HITS 处, 需人工审查)${NC}"
    grep -rn "private static" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null \
        | grep -v "private static final" | grep -v "import " | head -5
    WARN=$((WARN + 1))
else
    echo -e "${GREEN}PASS${NC}"
fi

# ----------------------------------------------------------------------------
# 规则 5: 禁止 Controller 使用 Map<String, Object> 作为请求体 (V9 防御)
# ----------------------------------------------------------------------------
echo -n "[规则5] 禁止 Controller 用 Map<String,Object> 接收 body... "
HITS=$(grep -rn "@RequestBody.*Map<String, Object>" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null \
    | wc -l | tr -d ' ')
if [ "$HITS" -gt 0 ]; then
    echo -e "${RED}FAIL ($HITS 处)${NC}"
    grep -rn "@RequestBody.*Map<String, Object>" micro-course-api/src/main/java/com/microcourse/controller/ 2>/dev/null
    FAIL=1
else
    echo -e "${GREEN}PASS${NC}"
fi

echo ""
if [ $FAIL -gt 0 ]; then
    echo -e "${RED}=== 预检 FAIL ===${NC}"
    exit 1
fi
echo -e "${GREEN}=== 预检 PASS ===${NC}"
exit 0