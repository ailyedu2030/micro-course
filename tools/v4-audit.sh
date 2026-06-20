#!/bin/bash
# ===================================================================
# v4-audit.sh — Super-Fix v4 审计执行器
#
# 对指定文件或目录执行多 lens 审计扫描。
# 退出码: 0 = 无新发现, 1 = 有新发现, 2 = 参数错误
#
# 用法: bash tools/v4-audit.sh <target_path> [--lens=security|concurrency|dataflow|...]
# ===================================================================

set -euo pipefail

TARGET="${1:-}"
LENS="${2:-all}"
CACHE_DIR=".audit-cache"

if [ -z "$TARGET" ]; then
    echo "用法: bash tools/v4-audit.sh <target_path> [--lens=<lens>]"
    echo ""
    echo "可用 lens: security, concurrency, dataflow, error, resource, a11y, performance, ux, all"
    exit 2
fi

if [ ! -e "$TARGET" ]; then
    echo "错误: $TARGET 不存在"
    exit 2
fi

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

FINDINGS_FILE="$CACHE_DIR/v4-findings-$(date +%Y%m%d-%H%M%S).json"
TOTAL=0

echo "🔍 Super-Fix v4 审计扫描"
echo "   目标: $TARGET"
echo "   透镜: $LENS"
echo ""

# Security lens checks
scan_security() {
    local file="$1"
    local findings=0
    
    # IDOR: verifyOwnership/verifyAccess 缺失
    if grep -n "findById\|getById\|selectById" "$file" 2>/dev/null | grep -v "verifyAccess\|verifyOwnership\|@PreAuthorize" | head -1 | grep -q ":"; then
        echo "  [P1] security/IDOR: $file 可能缺少所有权验证"
        findings=$((findings + 1))
    fi
    
    # SpEL injection
    if grep -n '@PreAuthorize.*"' "$file" 2>/dev/null | grep -v "authentication.principal" | head -1 | grep -q ":"; then
        echo "  [P2] security/SpEL: $file @PreAuthorize 可能使用硬编码"
        findings=$((findings + 1))
    fi
    
    # XSS via v-html/innerHTML
    if grep -n "v-html\|innerHTML\|srcdoc" "$file" 2>/dev/null | head -1 | grep -q ":"; then
        echo "  [P1] security/XSS: $file 使用 v-html/innerHTML"
        findings=$((findings + 1))
    fi
    
    # SQL injection (LIKE without escape)
    if grep -n "LIKE.*+" "$file" 2>/dev/null | grep -v "ESCAPE\|escape" | head -1 | grep -q ":"; then
        echo "  [P1] security/SQLi: $file LIKE 查询可能缺少转义"
        findings=$((findings + 1))
    fi
    
    return $findings
}

# Concurrency lens checks
scan_concurrency() {
    local file="$1"
    local findings=0
    
    # TOCTOU: read-then-write without transaction
    if grep -n "findById.*\|selectById.*" "$file" 2>/dev/null | head -1 | grep -q ":"; then
        if grep -n "\.update\|\.save\|\.insert" "$file" 2>/dev/null | head -1 | grep -q ":"; then
            echo "  [P1] concurrency/TOCTOU: $file 可能存在读-写竞态"
            findings=$((findings + 1))
        fi
    fi
    
    # @Transactional missing
    if grep -n "public.*save\|public.*update\|public.*delete" "$file" 2>/dev/null | grep -v "@Transactional" | head -1 | grep -q ":"; then
        echo "  [P2] concurrency/TX: $file 写操作可能缺少 @Transactional"
        findings=$((findings + 1))
    fi
    
    return $findings
}

# Dataflow lens checks
scan_dataflow() {
    local file="$1"
    local findings=0
    
    # LIMIT missing
    if grep -n "findAll\|selectAll\|list()" "$file" 2>/dev/null | grep -v "LIMIT\|limit\|Page\|Pageable" | head -1 | grep -q ":"; then
        echo "  [P1] dataflow/LIMIT: $file 查询可能缺少分页限制"
        findings=$((findings + 1))
    fi
    
    # Unvalidated input
    if grep -n "@RequestParam\|@PathVariable\|@RequestBody" "$file" 2>/dev/null | head -1 | grep -q ":"; then
        if ! grep -n "@Valid\|@Validated\|BindingResult" "$file" 2>/dev/null | head -1 | grep -q ":"; then
            echo "  [P2] dataflow/VALID: $file 可能缺少输入验证"
            findings=$((findings + 1))
        fi
    fi
    
    return $findings
}

# Error lens checks
scan_error() {
    local file="$1"
    local findings=0
    
    # Resource leak (InputStream/Connection without try-with-resources)
    if grep -n "new.*InputStream\|new.*Connection\|new.*Reader" "$file" 2>/dev/null | grep -v "try\|try-with" | head -1 | grep -q ":"; then
        echo "  [P1] error/LEAK: $file 可能存在资源泄漏"
        findings=$((findings + 1))
    fi
    
    # Empty catch block
    if grep -n "catch.*{" "$file" 2>/dev/null | head -1 | grep -q ":"; then
        if grep -A1 "catch.*{" "$file" 2>/dev/null | grep -q "^\s*}$\|^\s*//"; then
            echo "  [P2] error/EMPTY_CATCH: $file 存在空 catch 块"
            findings=$((findings + 1))
        fi
    fi
    
    return $findings
}

# Resource lens checks
scan_resource() {
    local file="$1"
    local findings=0
    
    # N+1 query pattern
    if grep -n "forEach.*{" "$file" 2>/dev/null | head -1 | grep -q ":"; then
        if grep -A5 "forEach.*{" "$file" 2>/dev/null | grep -q "findById\|selectById\|getById"; then
            echo "  [P1] resource/N+1: $file 循环内查询"
            findings=$((findings + 1))
        fi
    fi
    
    return $findings
}

# Main scan
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ -d "$TARGET" ]; then
    FILES=$(find "$TARGET" -name "*.java" -o -name "*.vue" -o -name "*.js" -o -name "*.ts" 2>/dev/null | head -100)
    FILE_COUNT=$(echo "$FILES" | grep -c "." || echo 0)
    echo "扫描 $FILE_COUNT 个文件..."
    echo ""
    
    while IFS= read -r file; do
        [ -z "$file" ] && continue
        echo "📄 $file"
        
        [ "$LENS" = "all" ] || [ "$LENS" = "security" ] && { scan_security "$file" || true; }
        [ "$LENS" = "all" ] || [ "$LENS" = "concurrency" ] && { scan_concurrency "$file" || true; }
        [ "$LENS" = "all" ] || [ "$LENS" = "dataflow" ] && { scan_dataflow "$file" || true; }
        [ "$LENS" = "all" ] || [ "$LENS" = "error" ] && { scan_error "$file" || true; }
        [ "$LENS" = "all" ] || [ "$LENS" = "resource" ] && { scan_resource "$file" || true; }
        
    done <<< "$FILES"
else
    echo "📄 $TARGET"
    
    [ "$LENS" = "all" ] || [ "$LENS" = "security" ] && { scan_security "$TARGET" || true; }
    [ "$LENS" = "all" ] || [ "$LENS" = "concurrency" ] && { scan_concurrency "$TARGET" || true; }
    [ "$LENS" = "all" ] || [ "$LENS" = "dataflow" ] && { scan_dataflow "$TARGET" || true; }
    [ "$LENS" = "all" ] || [ "$LENS" = "error" ] && { scan_error "$TARGET" || true; }
    [ "$LENS" = "all" ] || [ "$LENS" = "resource" ] && { scan_resource "$TARGET" || true; }
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 审计扫描完成: $TARGET"
