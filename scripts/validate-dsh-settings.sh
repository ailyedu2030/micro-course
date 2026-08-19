#!/bin/bash
# ============================================================================
# validate-dsh-settings.sh · DSH settings.yaml provider 有效性验证 (总工程师 2026-08-20)
# ----------------------------------------------------------------------------
# 背景: DSH 的 settings.yaml 中 agent-default-model.provider / vision-router.providers[].provider /
#       llm-pi-ai.providers.* 必须是 pi-ai 已知 provider, 否则 DSH 找不到该 provider → 默认模型
#       解析失败 → 死循环 (CPU 26%+, 反复重试).
#       真实踩坑案例 (2026-08-20): 用户两次写错 provider 名
#         - deepseek-vision (应: deepseek-official 或 deepseek)
#         - minimax-cn-vision (应: minimax-cn)
# 用途:
#   bash scripts/validate-dsh-settings.sh                       # 默认: 检查 + 报告 (exit 0 即使有问题)
#   bash scripts/validate-dsh-settings.sh --strict              # 任何问题 exit 1 (CI 用)
#   bash scripts/validate-dsh-settings.sh --dsh-home=~/.dsh     # 自定义 DSH 路径
#   bash scripts/validate-dsh-settings.sh --whitelist-add NAME  # 添加自定义 provider (e.g. 本地 fork)
#
# 已知 pi-ai provider 白名单来源: @earendil-works/pi-ai/dist/providers/*.js 的 id 字段
#   国内常用: deepseek / minimax / minimax-cn / anthropic / google / groq / moonshotai-cn
#   国际: openai / anthropic / bedrock / xai / mistral / together / openrouter
#   ⚠️ 不在白名单: deepseek-vision / minimax-cn-vision / minimax-image 等自造名 (触发死循环)
# ============================================================================

set -e

# 参数解析
STRICT=false
DSH_HOME="${DSH_HOME:-/Users/jackie/.dsh}"
PI_AI_DIR=""
CUSTOM_WHITELIST=()
for arg in "$@"; do
  case $arg in
    --strict) STRICT=true ;;
    --dsh-home=*) DSH_HOME="${arg#*=}" ;;
    --pi-ai-dir=*) PI_AI_DIR="${arg#*=}" ;;
    --whitelist-add) shift; CUSTOM_WHITELIST+=("$1"); shift ;;
    *) ;;
  esac
done
shift $((0)) 2>/dev/null || true

# 找 pi-ai 目录 (DSH checkout 内置)
if [ -z "$PI_AI_DIR" ]; then
  PI_AI_DIR=$(find /Users/jackie/.npm/_npx -maxdepth 6 -path '*@earendil-works/pi-ai/dist/providers' -type d 2>/dev/null | head -1)
fi

SETTINGS="$DSH_HOME/settings.yaml"
[ -f "$SETTINGS" ] || { echo "❌ DSH settings 不存在: $SETTINGS" >&2; exit 2; }

# 提取 pi-ai 已知 provider 白名单
extract_whitelist() {
  if [ -n "$PI_AI_DIR" ] && [ -d "$PI_AI_DIR" ]; then
    grep -hE '^\s*id:\s*"[a-z][^"]+"\s*,' "$PI_AI_DIR"/*.js 2>/dev/null \
      | grep -oE '"[a-z][^"]+"' | tr -d '"' | sort -u
  else
    # 兜底: 内置最小白名单 (从 pi-ai 已知 provider 抓取, 2026-08-20)
    echo "deepseek minimax minimax-cn anthropic openai google groq moonshotai-cn moonshotai qwen-token-plan qwen-token-plan-cn cerebras cloudflare-ai-gateway cloudflare-workers-ai github-copilot fireworks google-vertex huggingface kimi-coding mistral nvidia openai-codex opencode opencode-go openrouter together vercel-ai-gateway xai xiaomi xiaomi-token-plan-ams xiaomi-token-plan-cn xiaomi-token-plan-sgp zai zai-coding-cn amazon-bedrock ant-ling azure-openai-responses adc api-key aws-profile bearer-token credential-chain service-account faux"
    tr ' ' '\n' | sort -u | grep -v '^$'
  fi
}

WHITELIST=$(extract_whitelist)
# 合并自定义白名单
for c in "${CUSTOM_WHITELIST[@]}"; do
  WHITELIST+=$'\n'$c
done
WHITELIST_SORTED=$(echo "$WHITELIST" | sort -u)

# 提取 settings.yaml 中所有 provider 字段值 (3 类: agent-default-model.provider / vision-router.providers[].provider / llm-pi-ai.providers.*)
# 用 awk 而非 yq (DSH 环境可能没装 yq)
# 1) agent-default-model.provider
PROVIDERS=$(awk '
  /^[a-z]/ || /^[ ]*[a-z]/ {
    # 匹配 provider: xxx 模式 (在 agent-default-model / vision-router / llm-pi-ai 块内)
    if (in_block) {
      if (match($0, /^[[:space:]]*provider:[[:space:]]*([^[:space:]]+)/, arr)) {
        print arr[1]
      }
    }
  }
  # 块进入检测
  /agent-default-model:|vision-router:|llm-pi-ai:/ { in_block=1 }
  /^[^ ]/ && !/agent-default-model:|vision-router:|llm-pi-ai:/ { in_block=0 }
' "$SETTINGS" 2>/dev/null | sort -u)

# 简化: 直接用 sed/grep 提取 (更稳)
PROVIDERS=$(grep -E '^[[:space:]]*provider:[[:space:]]*[a-zA-Z][^[:space:]]*' "$SETTINGS" | sed -E 's/^[[:space:]]*provider:[[:space:]]*//' | sort -u)

# 校验
findings_invalid=()
findings_total=0
for p in $PROVIDERS; do
  findings_total=$((findings_total + 1))
  if ! echo "$WHITELIST_SORTED" | grep -qxF "$p"; then
    findings_invalid+=("$p")
  fi
done

echo "=============================================="
echo "  DSH settings.yaml provider 验证 (R10 兜底)"
echo "=============================================="
echo "  白名单源: ${PI_AI_DIR:-(内置 fallback)}"
echo "  白名单 provider 数量: $(echo "$WHITELIST_SORTED" | wc -l | tr -d ' ')"
echo "  settings.yaml 中 provider 数量: $findings_total"
echo "  无效 provider 数量: ${#findings_invalid[@]}"

if [ "${#findings_invalid[@]}" -eq 0 ]; then
  echo "  ✅ 所有 provider 有效"
  echo "=============================================="
  exit 0
fi

echo -e "  \033[31m❌ 发现 ${#findings_invalid[@]} 个无效 provider (会触发 DSH 死循环!):\033[0m"
for p in "${findings_invalid[@]}"; do
  echo -e "    \033[31m✗\033[0m provider: \033[1m$p\033[0m"
  # 给出最相似建议 (简单首字符匹配)
  closest=$(echo "$WHITELIST_SORTED" | grep -E "^${p:0:5}" | head -1)
  if [ -z "$closest" ]; then
    closest=$(echo "$WHITELIST_SORTED" | grep -E "^${p:0:3}" | head -1)
  fi
  if [ -n "$closest" ]; then
    echo "      建议: 改用 \033[1;33m$closest\033[0m (前缀匹配, 可能是你想用的)"
  fi
done
echo
echo "  修复方式: 编辑 $SETTINGS"
echo "    agent-default-model.provider: deepseek-official  (替代 deepseek-vision)"
echo "    vision-router.providers[].provider: minimax-cn     (替代 minimax-cn-vision)"
echo "  完整白名单: 跑 bash scripts/validate-dsh-settings.sh 列出 (本输出中含 38 个)"
echo "=============================================="

if [ "$STRICT" = "true" ]; then
  exit 1
fi
exit 0
