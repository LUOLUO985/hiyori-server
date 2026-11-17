#!/usr/bin/env bash
# 服务器第一次初始化（在服务器上运行一次）：
#   cd /srv/hiyori && sudo bash deploy/server-setup.sh
#
# 做的事：加 2G 虚拟内存、装 Docker 和 Nginx、配国内镜像加速、生成 .env、
#         配好 Nginx（先用 IP 访问）、每天凌晨自动备份数据库、启动服务。
# 可以重复运行，不会重复添加。
set -euo pipefail
cd "$(dirname "$0")/.."
APP_DIR="$(pwd)"
APP_USER="${SUDO_USER:-ubuntu}"

if [ "$(id -u)" != "0" ]; then echo "请用 sudo 运行：sudo bash deploy/server-setup.sh"; exit 1; fi

echo "== 1/6 虚拟内存（2G 内存机器跑数据库 + Java 需要它兜底）"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10 >/dev/null && echo 'vm.swappiness=10' > /etc/sysctl.d/99-hiyori.conf
fi
free -h | sed -n 1,3p

echo "== 2/6 安装 Docker、Nginx"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q docker.io docker-compose-v2 nginx curl
# 国内服务器直接拉 Docker Hub 很慢或失败，用腾讯云内网镜像
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json <<'JSON'
{ "registry-mirrors": ["https://mirror.ccs.tencentyun.com"] }
JSON
fi
systemctl enable --now docker
systemctl restart docker
usermod -aG docker "$APP_USER" || true

echo "== 3/6 生成 .env（已有就不动）"
PUBLIC_IP="$(curl -s --max-time 5 http://metadata.tencentyun.com/latest/meta-data/public-ipv4 || true)"
[ -n "$PUBLIC_IP" ] || PUBLIC_IP="$(curl -s --max-time 5 https://ifconfig.me || echo 127.0.0.1)"
if [ ! -f .env ]; then
  rand() { tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1"; }
  sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(rand 24)|" \
      -e "s|^DB_ROOT_PASSWORD=.*|DB_ROOT_PASSWORD=$(rand 24)|" \
      -e "s|^APP_JWT_SECRET=.*|APP_JWT_SECRET=$(rand 48)|" \
      -e "s|^APP_PUBLIC_BASE_URL=.*|APP_PUBLIC_BASE_URL=http://$PUBLIC_IP/files|" \
      -e "s|^APP_CORS_ORIGINS=.*|APP_CORS_ORIGINS=http://$PUBLIC_IP|" \
      .env.example > .env
  chmod 600 .env && chown "$APP_USER": .env
  echo "已生成 .env（数据库密码、JWT 密钥都是随机的）"
fi

echo "== 4/6 Nginx：先用 IP 访问（备案后再换成域名 + HTTPS）"
sed "s|__APP_DIR__|$APP_DIR|g" deploy/nginx-ip.conf > /etc/nginx/sites-available/hiyori
ln -sf /etc/nginx/sites-available/hiyori /etc/nginx/sites-enabled/hiyori
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo "== 5/6 每天 03:30 自动备份数据库，保留 14 天"
mkdir -p "$APP_DIR/backups" && chown "$APP_USER": "$APP_DIR/backups"
cat > /etc/cron.d/hiyori-backup <<CRON
30 3 * * * root cd $APP_DIR && bash deploy/backup.sh >> $APP_DIR/backups/backup.log 2>&1
CRON

echo "== 6/6 启动"
docker compose up -d --build
echo "等后端启动（第一次要初始化数据库，大约 1 分钟）…"
for i in $(seq 1 60); do
  if curl -fs http://127.0.0.1:8080/api/categories >/dev/null; then break; fi; sleep 3
done
if curl -fs http://127.0.0.1/api/categories >/dev/null; then
  echo
  echo "✅ 部署成功：http://$PUBLIC_IP/api/categories"
  echo "   小程序 utils/config.js 的 test 地址填：http://$PUBLIC_IP"
else
  echo "❌ 后端没起来，看日志：docker compose logs --tail 80 server"; exit 1
fi
