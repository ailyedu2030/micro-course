#!/bin/bash
# precheck.sh — 编码前预检脚本
# 退出码 != 0 → 禁止写代码

set -e

echo "🔍 Running pre-check..."

# 1. 检查后端编译
echo "[1/3] Backend compile check..."
cd micro-course-api
mvn compile -q 2>/dev/null || { echo "❌ Backend compile failed"; exit 1; }
echo "✅ Backend OK"

# 2. 检查前端 lint
echo "[2/3] Frontend lint check..."
cd ../micro-course-admin
npm run lint 2>/dev/null || { echo "❌ Frontend lint failed"; exit 1; }
echo "✅ Frontend OK"

# 3. 检查 git 状态
echo "[3/3] Git status..."
cd ..
git status --short

echo "✅ All pre-checks passed"
