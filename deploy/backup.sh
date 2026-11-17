#!/usr/bin/env bash
# 导出数据库到 backups/，保留最近 14 份。服务器上每天 03:30 自动运行；也可以手动运行。
# 恢复：gunzip -c backups/hiyori-XXXX.sql.gz | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" hiyori'
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
f="backups/hiyori-$(date +%Y%m%d-%H%M).sql.gz"
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines hiyori' | gzip > "$f"
ls -1t backups/hiyori-*.sql.gz | tail -n +15 | xargs -r rm -f
echo "$(date '+%F %T') 备份完成：$f ($(du -h "$f" | cut -f1))"
