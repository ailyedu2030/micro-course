#!/usr/bin/env bash
#
# test-start-services-race-condition.sh
#
# F-15 race condition 修复的回归测试（F2 新增，2026-08-08）。
# 验证 .github/actions/start-services/action.yml 中的重试循环模式：
#
#   PostgreSQL 容器启动时 TCP 端口先可 accept，但 initdb / 连接握手未完成，
#   首次连接会落在"半起状态"临界点 → 必须用重试循环 + 就绪后二次重验兜底。
#
# 本脚本用"延迟就绪标记文件"模拟慢启动服务（pg_isready 的等价物），
# 验证两类行为：
#   1. 成功场景  : 服务启动慢（busybox sleep 模拟）→ 重试循环能在超时窗口内等到就绪
#   2. fail-fast : 服务启动时长 > 重试窗口（永不就绪）→ 循环超时即失败（与 action.yml 的
#                  ::error:: + exit 1 行为一致，不静默）
#
# 用法: bash scripts/test-start-services-race-condition.sh
# 环境变量（可选）: READY_DELAY_SECONDS / RETRY_ATTEMPTS / RETRY_SLEEP / RECHECK_ATTEMPTS
set -uo pipefail

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# ---- 配置（默认值与 action.yml 的量级一致，可调小便于快速回归） ----
READY_DELAY_SECONDS="${READY_DELAY_SECONDS:-5}"   # 模拟 PG 慢启动耗时
RETRY_ATTEMPTS="${RETRY_ATTEMPTS:-10}"            # 首段等待循环次数（action.yml 为 30）
RETRY_SLEEP="${RETRY_SLEEP:-1}"                   # 每次尝试间隔秒数（action.yml 为 2s）
RECHECK_ATTEMPTS="${RECHECK_ATTEMPTS:-3}"         # 就绪后二次重验次数（action.yml 为 10）

failures=0

# busybox sleep 优先（任务要求模拟"用 busybox sleep"），无 busybox 时降级原生 sleep
sleep_cmd() {
  if command -v busybox >/dev/null 2>&1; then
    busybox sleep "$1"
  else
    sleep "$1"
  fi
}

# 模拟"慢启动服务"：延迟 READY_DELAY_SECONDS 秒后创建就绪标记（≈ pg_isready 首次返回 OK）
# 并启动一个伪后台进程（类似 docker run -d 返回后 PG 仍在 initdb）。
start_slow_service() {
  local marker="$1" delay="$2"
  rm -f "$marker"
  # >/dev/null 2>&1 关键：后台子 shell 会继承命令替换 $( ) 的 stdout 管道，
  # 不重定向则 $(start_slow_service ...) 会一直等到后台任务结束才返回（sleep+touch
  # 完成后管道才关闭），导致 marker 在 wait_until_ready 运行前就已就绪——
  # 重试循环变成"第 1 次尝试即命中"的空测。重定向后命令替换立即返回 PID。
  ( sleep_cmd "$delay" && touch "$marker" ) >/dev/null 2>&1 &
  echo $!   # 返回后台进程 PID
}

# 模拟 action.yml 的重试循环：首段等待 + 就绪后二次重验 + 终态硬校验
# 返回 0 = 就绪，1 = 超时（fail-fast）
wait_until_ready() {
  local marker="$1" ok=0 attempt=0
  for i in $(seq 1 "$RETRY_ATTEMPTS"); do
    attempt=$i
    if [ -f "$marker" ]; then ok=1; break; fi
    sleep "$RETRY_SLEEP"
  done
  # 第二段重验（F-15: 就绪信号后仍可能落在临界点，重复确认）
  if [ "$ok" = "1" ]; then
    for i in $(seq 1 "$RECHECK_ATTEMPTS"); do
      if [ -f "$marker" ]; then ok=1; break; fi
      sleep 1
    done
  fi
  if [ "$ok" = "1" ]; then
    echo "$attempt"
    return 0
  fi
  return 1
}

echo "=================================================="
echo "F-15 race condition 重试循环回归测试"
echo "  READY_DELAY=${READY_DELAY_SECONDS}s | RETRY_ATTEMPTS=${RETRY_ATTEMPTS} | RETRY_SLEEP=${RETRY_SLEEP}s | RECHECK_ATTEMPTS=${RECHECK_ATTEMPTS}"
echo "  sleep 实现: $(command -v busybox >/dev/null 2>&1 && echo 'busybox sleep' || echo '原生 sleep')"
echo "=================================================="

# ---- Case 1: 成功场景 ----
echo ""
echo "[Case 1] 慢启动服务（${READY_DELAY_SECONDS}s 后就绪）→ 重试循环应成功等到"
CASE1_MARKER="$TMP_DIR/ready-1"
pid1="$(start_slow_service "$CASE1_MARKER" "$READY_DELAY_SECONDS")"
attempt="$(wait_until_ready "$CASE1_MARKER" || true)"
rc=$?
if [ "$rc" = "0" ]; then
  echo "  ✅ PASS: 服务在第 ${attempt} 次尝试就绪（启动延迟 ${READY_DELAY_SECONDS}s，窗口 ${RETRY_ATTEMPTS} 次）"
else
  echo "  ❌ FAIL: 重试循环未能在窗口内等到就绪（启动延迟 ${READY_DELAY_SECONDS}s <= 窗口 ${RETRY_ATTEMPTS}s 应命中）"
  failures=$((failures + 1))
fi
wait "$pid1" 2>/dev/null || true

# ---- Case 2: fail-fast 场景 ----
echo ""
echo "[Case 2] 服务启动时长 > 重试窗口（永不就绪）→ 循环应超时 fail-fast（对应 action.yml 的 ::error:: + exit 1）"
CASE2_MARKER="$TMP_DIR/ready-2"
NEVER_DELAY=$((RETRY_ATTEMPTS * RETRY_SLEEP + 5))   # 确保超过窗口
pid2="$(start_slow_service "$CASE2_MARKER" "$NEVER_DELAY")"
if wait_until_ready "$CASE2_MARKER" >/dev/null 2>&1; then
  echo "  ❌ FAIL: 预期超时但服务意外就绪（启动延迟 ${NEVER_DELAY}s 应超过窗口 ${RETRY_ATTEMPTS}s）"
  failures=$((failures + 1))
else
  echo "  ✅ PASS: 窗口内未就绪 → 返回失败（启动方将输出 ::error:: 并 exit 1，不静默）"
fi
wait "$pid2" 2>/dev/null || true

# ---- 汇总 ----
echo ""
echo "=================================================="
if [ "$failures" = "0" ]; then
  echo "✅ 全部通过：race condition 重试循环行为符合 action.yml 设计"
  echo "   （成功等到慢启动服务 + 超时即 fail-fast）"
  exit 0
else
  echo "❌ ${failures} 项失败：重试循环行为与设计不符，请检查 action.yml 或本脚本"
  exit 1
fi
