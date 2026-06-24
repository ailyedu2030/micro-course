#!/bin/bash
# commit-msg hook — 在 .git/hooks/commit-msg 安装此文件后每次 commit 自动校验
# 安装: ln -sf ../../.claude/skills/microcourse/scripts/validate-commit-message.sh .git/hooks/commit-msg
exec bash "$(git rev-parse --show-toplevel)/.claude/skills/microcourse/scripts/validate-commit-message.sh" "$1"
