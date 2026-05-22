# 网络图文发布平台启动脚本

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "网络图文发布平台 - 一键启动" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# 步骤1: 检查并创建 .env 文件
Write-Host "`n[1/3] 检查配置文件..." -ForegroundColor Yellow
if (!(Test-Path ".env")) {
    Write-Host "  → 复制 .env.example 到 .env" -ForegroundColor Green
    Copy-Item ".env.example" ".env"
} else {
    Write-Host "  ✓ .env 已存在" -ForegroundColor Green
}

# 步骤2: 安装依赖
Write-Host "`n[2/3] 安装依赖包..." -ForegroundColor Yellow
Write-Host "  → 执行: python -m pip install -r requirements.txt" -ForegroundColor Green
python -m pip install -r requirements.txt

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ 依赖安装失败，请检查 Python 环境" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ 依赖安装完成" -ForegroundColor Green

# 步骤3: 启动服务
Write-Host "`n[3/3] 启动 FastAPI 服务..." -ForegroundColor Yellow
Write-Host "  → 执行: python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000" -ForegroundColor Green
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "服务即将启动，访问地址：" -ForegroundColor Cyan
Write-Host "  • 前端首页: http://127.0.0.1:8000/index.html" -ForegroundColor Green
Write-Host "  • API 文档: http://127.0.0.1:8000/docs" -ForegroundColor Green
Write-Host "`n默认账号:" -ForegroundColor Cyan
Write-Host "  • 管理员: admin / Admin@123456" -ForegroundColor Green
Write-Host "  • 用户: user / User@123456" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000
