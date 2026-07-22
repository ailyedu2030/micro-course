#!/bin/bash
# 备份并重建 Trae redhat.java 的 JDT 工作区缓存，解决切分支后的 Java 假报错。
set -euo pipefail

WORKSPACE_ROOT="${TRAE_WORKSPACE_STORAGE:-$HOME/Library/Application Support/Trae/User/workspaceStorage}"
PROJECT_MARKER="${1:-micro-course-api}"
TIMESTAMP="$(date +"%Y%m%d-%H%M%S")"

if [ ! -d "$WORKSPACE_ROOT" ]; then
    echo "ERROR: 未找到 Trae workspaceStorage: $WORKSPACE_ROOT"
    exit 1
fi

shopt -s nullglob
matches=()
for redhat_dir in "$WORKSPACE_ROOT"/*/redhat.java; do
    jdt_ws="$redhat_dir/jdt_ws"
    project_dir="$jdt_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/$PROJECT_MARKER"
    if [ -d "$jdt_ws" ] && [ -d "$project_dir" ]; then
        matches+=("$jdt_ws")
    fi
done
shopt -u nullglob

if [ "${#matches[@]}" -eq 0 ]; then
    echo "ERROR: 未找到包含项目 [$PROJECT_MARKER] 的 Trae Java 工作区缓存。"
    echo "提示: 可传入项目标识，例如: bash scripts/reset-trae-java-cache.sh micro-course-api"
    exit 1
fi

echo "发现 ${#matches[@]} 个 Trae Java 工作区缓存:"
for jdt_ws in "${matches[@]}"; do
    echo "  - $jdt_ws"
done

for jdt_ws in "${matches[@]}"; do
    backup_dir="${jdt_ws}.backup-${TIMESTAMP}"
    echo ""
    echo "处理: $jdt_ws"
    mv "$jdt_ws" "$backup_dir"
    mkdir -p "$jdt_ws"
    echo "  备份完成 -> $backup_dir"
    echo "  已创建新的空工作区 -> $jdt_ws"
done

echo ""
echo "下一步:"
echo "1. 回到 Trae 并重载窗口，或等待 redhat.java 自动重启。"
echo "2. 重新打开 Java 文件，等待索引重建。"
echo "3. 若需回滚，可将 *.backup-${TIMESTAMP} 改回 jdt_ws。"
