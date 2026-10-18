#!/usr/bin/env bash
# 将本地仓库一键推送到 GitHub
#
# 用法:
#   ./tools/push_to_github.sh https://github.com/<用户名>/<仓库名>.git
#
# 前置条件:
#   1) GitHub 上已存在该仓库（空仓库即可，不要勾选自动生成 README）
#   2) 本机已配置 GitHub 凭据（HTTPS: PAT / Git Credential Manager；或改用 SSH 地址）
#
# 若使用 SSH 地址（git@github.com:<用户名>/<仓库名>.git），需已配置 SSH key。
set -euo pipefail

REMOTE="${1:-}"
if [ -z "$REMOTE" ]; then
  echo "用法: $0 <仓库URL>"
  echo "示例: $0 https://github.com/mwenying/platform-plus.git"
  exit 1
fi

cd "$(dirname "$0")/.."

echo "==> 仓库: $(pwd)"
echo "==> 提交: $(git log --oneline -1)"
echo "==> 文件: $(git ls-files | wc -l) 个"

git remote remove origin 2>/dev/null || true
git remote add origin "$REMOTE"

# 统一主分支名为 main
git branch -M main

echo "==> 推送中..."
git push -u origin main

echo "==> 完成: $REMOTE (main)"
