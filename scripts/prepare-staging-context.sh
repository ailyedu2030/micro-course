#!/usr/bin/env bash
# =============================================================================
# prepare-staging-context.sh
# 为 staging 人工执行生成提交快照、时间戳和备份标签，减少手工抄写错误。
#
# 用法:
#   bash scripts/prepare-staging-context.sh
#   bash scripts/prepare-staging-context.sh --ref origin/main --format markdown
#   bash scripts/prepare-staging-context.sh --format env
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

TARGET_REF="origin/main"
OUTPUT_FORMAT="both"
FETCH_REMOTE=true

usage() {
  cat <<'EOF'
用法:
  bash scripts/prepare-staging-context.sh [--ref <git-ref>] [--format both|env|markdown] [--no-fetch]

参数:
  --ref <git-ref>         取值基准，默认 origin/main
  --format <format>       输出格式:
                          both     同时输出 env + markdown（默认）
                          env      只输出 shell 变量
                          markdown 只输出 markdown 回填片段
  --no-fetch              跳过 git fetch origin --prune
  -h, --help              显示帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)
      TARGET_REF="${2:-}"
      shift 2
      ;;
    --format)
      OUTPUT_FORMAT="${2:-}"
      shift 2
      ;;
    --no-fetch)
      FETCH_REMOTE=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "❌ 未知参数: $1" >&2
      usage
      exit 1
      ;;
  esac
done

case "$OUTPUT_FORMAT" in
  both|env|markdown) ;;
  *)
    echo "❌ 不支持的输出格式: $OUTPUT_FORMAT" >&2
    usage
    exit 1
    ;;
esac

cd "$PROJECT_ROOT"

if $FETCH_REMOTE; then
  git fetch origin --prune >/dev/null 2>&1
fi

if ! git rev-parse --verify "$TARGET_REF" >/dev/null 2>&1; then
  echo "❌ Git 引用不存在: $TARGET_REF" >&2
  exit 1
fi

DEPLOY_COMMIT="$(git rev-parse "$TARGET_REF")"
DEPLOY_SHORT_COMMIT="$(git rev-parse --short "$TARGET_REF")"
DEPLOY_TITLE="$(git log -1 --pretty=%s "$TARGET_REF")"
DEPLOY_ONELINE="$(git log -1 --oneline "$TARGET_REF")"
RELEASE_TS="$(date +%Y%m%d_%H%M%S)"
START_TIME="$(date '+%Y-%m-%d %H:%M:%S %Z')"
BACKUP_TAG="phase6_teacher_${RELEASE_TS}_${DEPLOY_SHORT_COMMIT}"

print_env() {
  cat <<EOF
export DEPLOY_REF="${TARGET_REF}"
export DEPLOY_COMMIT="${DEPLOY_COMMIT}"
export DEPLOY_SHORT_COMMIT="${DEPLOY_SHORT_COMMIT}"
export DEPLOY_TITLE="${DEPLOY_TITLE}"
export DEPLOY_ONELINE="${DEPLOY_ONELINE}"
export RELEASE_TS="${RELEASE_TS}"
export START_TIME="${START_TIME}"
export BACKUP_TAG="${BACKUP_TAG}"
EOF
}

print_markdown() {
  cat <<EOF
| 项目 | 内容 |
|---|---|
| 部署目标版本 / 提交 | \`${DEPLOY_COMMIT}\` |
| 部署目标提交标题 | ${DEPLOY_TITLE} |
| 执行开始时间 | ${START_TIME} |
| RELEASE_TS | \`${RELEASE_TS}\` |
| BACKUP_TAG | \`${BACKUP_TAG}\` |

\`\`\`bash
git fetch origin --prune
git rev-parse ${TARGET_REF}
git log --oneline -1 ${TARGET_REF}
date "+%Y-%m-%d %H:%M:%S %Z"
\`\`\`
EOF
}

case "$OUTPUT_FORMAT" in
  env)
    print_env
    ;;
  markdown)
    print_markdown
    ;;
  both)
    echo "# env"
    print_env
    echo ""
    echo "# markdown"
    print_markdown
    ;;
esac
