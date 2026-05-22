@echo off
chcp 65001 >nul

echo.
echo ============================================
echo 网络图文发布平台 - 一键启动（修复版）
echo ============================================
echo.

REM 步骤1: 检查并创建 .env 文件
echo [1/4] 检查配置文件...
if not exist ".env" (
    echo   ^> 复制 .env.example 到 .env
    copy .env.example .env >nul
) else (
    echo   √ .env 已存在
)

REM 步骤2: 卸载旧版本 SQLAlchemy
echo.
echo [2/4] 更新依赖包（卸载旧版SQLAlchemy）...
python -m pip uninstall -y SQLAlchemy >nul 2>&1

REM 步骤3: 重新安装所有依赖
echo   ^> 执行: python -m pip install -r requirements.txt
python -m pip install -r requirements.txt

if errorlevel 1 (
    echo   ✗ 依赖安装失败，请检查 Python 环境
    pause
    exit /b 1
)
echo   √ 依赖安装完成

REM 步骤4: 启动服务
echo.
echo [3/4] 启动 FastAPI 服务...
echo   ^> 执行: python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000
echo.
echo ============================================
echo 服务即将启动，访问地址：
echo   * 前端首页: http://127.0.0.1:8000/index.html
echo   * API 文档: http://127.0.0.1:8000/docs
echo.
echo 默认账号:
echo   * 管理员: admin / Admin@123456
echo   * 普通用户: user / User@123456
echo ============================================
echo.

python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000

pause
