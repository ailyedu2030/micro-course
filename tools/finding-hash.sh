#!/bin/bash
# ===================================================================
# finding-hash.sh — Super-Fix 发现哈希计算
#
# 为审计发现计算唯一哈希 ID（基于 file+line+description）。
# 退出码: 0 = 成功
#
# 用法: bash tools/finding-hash.sh <finding_json_file>
#       echo '{"file":"X.java","line":42,"description":"..."}' | bash tools/finding-hash.sh
# ===================================================================

set -euo pipefail

CACHE_DIR=".audit-cache"
FINDINGS_FILE="${1:-$CACHE_DIR/findings.json}"

GREEN='\033[0;32m'
NC='\033[0m'

if [ ! -f "$FINDINGS_FILE" ]; then
    echo "错误: $FINDINGS_FILE 不存在"
    exit 1
fi

echo "🔢 计算发现哈希..."

# 计算每个 finding 的哈希
if command -v jq &>/dev/null; then
    UPDATED=$(jq '
        .findings |= map(
            if .hash == null or .hash == "" then
                .hash = (.file + ":" + (.line // .line_range | tostring) + ":" + .description | @sha256 | .[0:8])
            else
                .
            end
        )
    ' "$FINDINGS_FILE")
    echo "$UPDATED" > "$FINDINGS_FILE"
    COUNT=$(jq '.findings | length' "$FINDINGS_FILE")
    echo -e "  [${GREEN}OK${NC}] 已计算 $COUNT 个发现的哈希"
else
    # Fallback: 使用 grep + md5sum
    echo "[WARN] jq 未安装，使用 grep fallback"
    while IFS= read -r line; do
        FILE=$(echo "$line" | grep -o '"file":"[^"]*"' | cut -d'"' -f4)
        DESC=$(echo "$line" | grep -o '"description":"[^"]*"' | cut -d'"' -f4)
        HASH=$(echo "${FILE}:${DESC}" | md5sum | cut -c1-8)
        echo "  $HASH: $FILE — $DESC"
    done < <(grep -o '{[^}]*}' "$FINDINGS_FILE")
fi

echo ""
echo "✅ 哈希计算完成"
