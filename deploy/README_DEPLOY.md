# Ubuntu 部署说明

## 1. 上传项目

把本项目上传到服务器：

```bash
/www/platform
```

## 2. 创建线上 MySQL 数据库

```bash
sudo mysql
```

```sql
CREATE DATABASE graphic_share_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'platform_user'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON graphic_share_platform.* TO 'platform_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

## 3. 配置 `.env`

```env
APP_NAME=Graphic Share Platform
SECRET_KEY=CHANGE_ME_LONG_RANDOM_SECRET
DATABASE_URL=mysql+pymysql://platform_user:CHANGE_ME_STRONG_PASSWORD@127.0.0.1:3306/graphic_share_platform?charset=utf8mb4
ACCESS_TOKEN_EXPIRE_MINUTES=1440
UPLOAD_DIR=uploads
CREATE_DEMO_DATA=false
```

## 4. 修改 Nginx 域名

编辑：

```bash
deploy/nginx-platform.conf
```

把 `YOUR_SERVER_IP_OR_DOMAIN` 改成服务器公网 IP 或域名。

## 5. 执行部署脚本

```bash
cd /www/platform
sudo bash deploy/deploy_ubuntu.sh
```

## 6. 访问

```text
http://服务器IP/index.html
```

查看状态：

```bash
sudo systemctl status platform
sudo journalctl -u platform -f
```
