#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/www/platform"
DB_NAME="graphic_share_platform"
DB_USER="platform_user"

echo "Install system packages"
apt update
apt install -y python3 python3-pip python3-venv mysql-server nginx

echo "Create Python environment"
cd "$APP_DIR"
python3 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

echo "Initialize MySQL database manually if you have not done it yet:"
echo "CREATE DATABASE ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
echo "CREATE USER '${DB_USER}'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';"
echo "GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';"
echo "FLUSH PRIVILEGES;"

echo "Install systemd service and nginx config"
cp deploy/platform.service /etc/systemd/system/platform.service
cp deploy/nginx-platform.conf /etc/nginx/sites-available/platform
ln -sf /etc/nginx/sites-available/platform /etc/nginx/sites-enabled/platform
nginx -t
systemctl daemon-reload
systemctl enable platform
systemctl restart platform
systemctl restart nginx

echo "Done. Check service with: systemctl status platform"
