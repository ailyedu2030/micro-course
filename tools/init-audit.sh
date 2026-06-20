#!/bin/bash
# ===================================================================
# init-audit.sh — Super-Fix 审计初始化
#
# 初始化 .audit-cache/ 目录、finding schema、baseline snapshot。
# 退出码: 0 = 初始化成功, 1 = 已存在（使用 --force 覆盖）
#
# 用法: bash tools/init-audit.sh [--force]
# ===================================================================

set -euo pipefail

FORCE=false
[[ "${1:-}" == "--force" ]] && FORCE=true

CACHE_DIR=".audit-cache"
SCHEMA_FILE="$CACHE_DIR/finding.schema.json"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ -d "$CACHE_DIR" ] && [ "$FORCE" != "true" ]; then
    echo -e "${YELLOW}[SKIP]${NC} $CACHE_DIR 已存在（使用 --force 覆盖）"
    exit 1
fi

echo "🔧 初始化 Super-Fix 审计缓存..."

mkdir -p "$CACHE_DIR"

# 创建 finding schema
if [ ! -f "$SCHEMA_FILE" ] || [ "$FORCE" = "true" ]; then
    cat > "$SCHEMA_FILE" << 'SCHEMA'
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Super-Fix Finding",
  "description": "Super-Fix 审计发现格式",
  "type": "object",
  "required": ["id", "severity", "lens", "file", "line", "description", "root_cause", "fix", "status", "hash"],
  "properties": {
    "id": { "type": "string", "pattern": "^SF-[0-9]{3}$" },
    "severity": { "type": "string", "enum": ["P0", "P1", "P2", "P3"] },
    "lens": { "type": "string", "enum": ["security", "concurrency", "dataflow", "error", "resource", "a11y", "performance", "ux"] },
    "file": { "type": "string" },
    "line": { "type": "integer", "minimum": 1 },
    "description": { "type": "string" },
    "root_cause": { "type": "string" },
    "fix": { "type": "string" },
    "status": { "type": "string", "enum": ["open", "fixed", "verified", "wontfix"] },
    "hash": { "type": "string", "pattern": "^[a-f0-9]{8}$" },
    "causal_chain": { "type": "array", "items": { "type": "string" } },
    "verified_at": { "type": "string", "format": "date-time" },
    "verified_by": { "type": "string" }
  }
}
SCHEMA
    echo -e "  [${GREEN}OK${NC}] $SCHEMA_FILE"
fi

# 创建 baseline state
cat > "$CACHE_DIR/audit_state.json" << 'STATE'
{
  "version": "v1.0",
  "project": "微课管理平台",
  "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
  "total_runs": 0,
  "total_findings": 0,
  "total_fixed": 0,
  "history": []
}
STATE
echo -e "  [${GREEN}OK${NC}] $CACHE_DIR/audit_state.json"

# 创建 findings.json 空数组
if [ ! -f "$CACHE_DIR/findings.json" ] || [ "$FORCE" = "true" ]; then
    echo "[]" > "$CACHE_DIR/findings.json"
    echo -e "  [${GREEN}OK${NC}] $CACHE_DIR/findings.json"
fi

# 创建 meta-review.md
if [ ! -f "$CACHE_DIR/meta-review.md" ] || [ "$FORCE" = "true" ]; then
    cat > "$CACHE_DIR/meta-review.md" << 'REVIEW'
# Super-Fix Meta-Review Report

> 自动生成 — 请勿手动编辑

## 审计概览
- 项目: 微课管理平台
- 框架: Super-Fix v1.0
- 状态: 初始化

## 发现统计
| 严重度 | 数量 | 状态 |
|:------:|:----:|:----:|
| P0 | 0 | - |
| P1 | 0 | - |
| P2 | 0 | - |
| P3 | 0 | - |

## 收敛维度
| 维度 | 状态 | 值 |
|:-----|:----:|:---:|
| 发现数量 | - | 0/0 |
| 构建状态 | - | - |
| 测试状态 | - | - |
| 冒烟测试 | - | - |
| 无回归 | - | - |

## 工具链完成度
- [x] convergence-check.sh
- [x] smoke-test.sh
- [x] sed-mutation-test.sh
- [x] gate-check.sh
- [ ] init-audit.sh ← 本次创建
- [ ] v4-audit.sh
- [ ] cross-run-dedup.sh
- [ ] finding-hash.sh
- [ ] regression-suite.sh
- [ ] after-action-review.ts
- [ ] chaos-test.sh
- [ ] validate-retry.ts
REVIEW
    echo -e "  [${GREEN}OK${NC}] $CACHE_DIR/meta-review.md"
fi

echo ""
echo "✅ 审计缓存初始化完成: $CACHE_DIR/"
