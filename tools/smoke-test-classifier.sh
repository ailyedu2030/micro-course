#!/bin/bash
# ===================================================================
# smoke-test-classifier.sh — smoke-test.sh 测试分类标记（P2-3）
#
# 用于 CI 中区分 smoke（启动期烟雾测试） vs integration（JUnit 集成测试）。
# 仅打印分类信息，无副作用、无网络请求，始终 exit 0。
# 用法: bash tools/smoke-test-classifier.sh
# ===================================================================

echo "=== smoke-test.sh 分类 ==="
echo "[1] 登录认证（启动期鉴权烟雾测试 · 必跑）"
echo "[2] 公开/认证 API（启动期健康检查 · 必跑）"
echo "[3] 角色权限（权限边界烟雾测试 · 必跑）"
echo "[4] 核心业务链路（已迁移到 JUnit 集成测试覆盖）"
echo "[5] 讲述稿/退出（启动期烟雾测试 · 必跑）"
echo "[6] 错误响应/权限回归（启动期烟雾测试 · 必跑）"
echo ""
echo "=== JUnit 集成测试覆盖（更全面，Phase B-3）==="
echo "- AuthFlowIntegrationTest          (10)"
echo "- EnrollmentFlowIntegrationTest    (8)"
echo "- VideoLearningFlowIntegrationTest (6)"
echo "- ExerciseFlowIntegrationTest      (8)"
echo "- NotificationFlowIntegrationTest  (6)"
echo "  合计 38 个核心链路集成测试"
echo ""
echo "=== CI 流水线顺序 ==="
echo "1) bash tools/smoke-test.sh   # 启动期烟雾测试（需运行中的服务）"
echo "2) mvn test                   # JUnit 集成测试（功能测试）"
echo "3) e2e（playwright）          # 端到端测试"
