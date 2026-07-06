#!/bin/bash
# ============================================================================
# check-permission.sh · 权限矩阵 v4.0 CI 门禁
# ----------------------------------------------------------------------------
# 验证: docs/permission-matrix-v4.0.yaml 中声明的端点都在代码中实现
# 运行: EndpointPermissionTest (Java 反射扫描 Controller @PreAuthorize)
#
# 用法:
#   bash scripts/check-permission.sh
#
# 退出码:
#   0 = PASS
#   1 = FAIL
# ============================================================================

set -e

YAML_FILE="docs/permission-matrix-v4.0.yaml"
TEST_CLASS="com.microcourse.security.EndpointPermissionTest"

echo "[Permission] 检查 YAML 文件存在..."
if [ ! -f "$YAML_FILE" ]; then
    echo "[Permission] ERROR: $YAML_FILE 不存在"
    exit 1
fi

echo "[Permission] 运行 EndpointPermissionTest..."
cd micro-course-api && mvn test -q -o -Dtest="$TEST_CLASS" 2>&1 | tail -10
TEST_EXIT=$?
cd ..

if [ $TEST_EXIT -ne 0 ]; then
    echo "[Permission] FAIL: 权限矩阵测试未通过"
    exit 1
fi

echo "[Permission] ✅ PASS"
exit 0