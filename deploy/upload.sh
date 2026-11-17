#!/usr/bin/env bash
# 在你自己的电脑上运行：打包后端，把代码 + 商品图同步到服务器，并重启后端。
#
#   ./deploy/upload.sh ubuntu@服务器IP
#
# 第一次运行后，按提示登录服务器执行一次 server-setup.sh；以后每次改完代码 / 补了图片，再跑这一条就行。
# rsync 只传有变化的文件，所以第二次起很快。
set -euo pipefail

TARGET="${1:?用法：./deploy/upload.sh ubuntu@服务器IP}"
REMOTE_DIR="${2:-/srv/hiyori}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

echo "== 1/3 打包后端"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
mvn -q -B package -DskipTests
mkdir -p dist && cp target/hiyori-mall-server-*.jar dist/app.jar
ls -lh dist/app.jar

echo "== 2/3 同步到 $TARGET:$REMOTE_DIR"
ssh "$TARGET" "command -v rsync >/dev/null || sudo apt-get install -y -q rsync; sudo mkdir -p '$REMOTE_DIR' && sudo chown \$(whoami): '$REMOTE_DIR'"
# macOS 自带的是 openrsync，不认 --info=progress2，这里只用通用参数
rsync -az --delete \
  --exclude target/ --exclude src/ --exclude logs/ --exclude .env --exclude backups/ \
  --exclude .DS_Store --exclude catalog/review/ \
  "$HERE/" "$TARGET:$REMOTE_DIR/"

echo "== 3/3 重启后端"
if ssh "$TARGET" "test -f '$REMOTE_DIR/.env'"; then
  ssh "$TARGET" "cd '$REMOTE_DIR' && docker compose up -d --build server && docker image prune -f >/dev/null"
  echo "✅ 已更新。几十秒后生效。"
else
  echo
  echo "第一次部署，还要初始化服务器（只需一次）："
  echo "  ssh $TARGET"
  echo "  cd $REMOTE_DIR && sudo bash deploy/server-setup.sh"
fi
