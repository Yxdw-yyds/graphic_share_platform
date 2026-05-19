# 网络图文发布平台

课程可演示版：静态 HTML/CSS/JS 前端 + FastAPI 后端 + MySQL/SQLAlchemy。

## 运行

1. 安装依赖：

```powershell
python -m pip install -r requirements.txt
```

2. 创建数据库：

```sql
SOURCE init_db.sql;
```

3. 复制配置：

```powershell
Copy-Item .env.example .env
```

按本机 MySQL 修改 `.env` 中的 `DATABASE_URL`。

4. 启动服务：

```powershell
python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000
```

访问：

- 前端首页：http://127.0.0.1:8000/index.html
- OpenAPI 文档：http://127.0.0.1:8000/docs

## 默认账号

- 管理员：`admin / Admin@123456`
- 普通用户：`user / User@123456`

验证码接口为课程演示版，会直接返回验证码并由前端弹窗显示。
