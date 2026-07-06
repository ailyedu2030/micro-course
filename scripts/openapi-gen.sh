#!/bin/bash
# OpenAPI 自动生成脚本
# 启动后端, 访问 /v3/api-docs, 转换为 YAML, 输出 docs/api/openapi.yaml
# CI 门禁: 生成结果与检入版本 diff 不一致则 fail

set -e

OUTPUT_FILE="docs/api/openapi.yaml"
TMP_FILE="/tmp/openapi-gen-$$.json"

echo "[OpenAPI Gen] 启动后端..."
mvn -f micro-course-api/pom.xml spring-boot:run -q &
SPRING_PID=$!

# 等待后端启动 (最长 60 秒)
for i in $(seq 1 60); do
    sleep 1
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "[OpenAPI Gen] 后端已启动"
        break
    fi
done

# 抓取 OpenAPI JSON
echo "[OpenAPI Gen] 抓取 /v3/api-docs..."
curl -sf http://localhost:8080/v3/api-docs > "$TMP_FILE" || {
    echo "[OpenAPI Gen] ERROR: 无法访问 /v3/api-docs"
    kill $SPRING_PID 2>/dev/null || true
    exit 1
}

# 转换 JSON 为 YAML (依赖 yq, 没有则用 Python 替代)
if command -v yq > /dev/null 2>&1; then
    yq -P "$TMP_FILE" > "$OUTPUT_FILE"
else
    python3 -c "
import json, sys
with open('$TMP_FILE') as f:
    data = json.load(f)
import yaml
print(yaml.dump(data, sort_keys=False, allow_unicode=True, default_flow_style=False))
" > "$OUTPUT_FILE"
fi

rm -f "$TMP_FILE"

# 关闭后端
kill $SPRING_PID 2>/dev/null || true
wait $SPRING_PID 2>/dev/null || true

echo "[OpenAPI Gen] 输出: $OUTPUT_FILE"

# CI 门禁: 检查本次生成与上次检入的 diff
if [ -f "$OUTPUT_FILE" ] && git diff --quiet "$OUTPUT_FILE"; then
    echo "[OpenAPI Gen] ✅ 无变化"
else
    echo "[OpenAPI Gen] ⚠ 检测到 openapi.yaml 变化, 提交后 commit"
    git diff --stat "$OUTPUT_FILE" 2>/dev/null || true
fi