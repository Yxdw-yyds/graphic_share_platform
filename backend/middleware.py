import logging
import time
from logging.handlers import RotatingFileHandler
from pathlib import Path

from fastapi import FastAPI, Request, status
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

logger = logging.getLogger("acfun")


def configure_logging(log_dir: str = "logs", level: int = logging.INFO) -> None:
    Path(log_dir).mkdir(parents=True, exist_ok=True)
    if logger.handlers:
        return
    logger.setLevel(level)
    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", "%Y-%m-%d %H:%M:%S")
    file_handler = RotatingFileHandler(
        Path(log_dir) / "app.log", maxBytes=2 * 1024 * 1024, backupCount=3, encoding="utf-8"
    )
    file_handler.setFormatter(fmt)
    console = logging.StreamHandler()
    console.setFormatter(fmt)
    logger.addHandler(file_handler)
    logger.addHandler(console)


def install(app: FastAPI) -> None:
    """注册全局错误处理 + 请求日志中间件。"""
    configure_logging()

    @app.middleware("http")
    async def log_requests(request: Request, call_next):
        start = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            duration = (time.perf_counter() - start) * 1000
            logger.exception(
                "REQ %s %s -> 500 %.1fms", request.method, request.url.path, duration
            )
            return JSONResponse(status_code=500, content={"detail": "服务器内部错误"})
        duration = (time.perf_counter() - start) * 1000
        user_id = request.headers.get("x-user-id", "-")
        logger.info(
            "REQ %s %s -> %d %.1fms user=%s",
            request.method,
            request.url.path,
            response.status_code,
            duration,
            user_id,
        )
        if request.url.path.endswith((".html", ".js", ".css")):
            response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
            response.headers["Pragma"] = "no-cache"
            response.headers["Expires"] = "0"
        return response

    @app.exception_handler(SQLAlchemyError)
    async def _sa_handler(_: Request, exc: SQLAlchemyError):
        logger.error("DB error: %s", exc)
        return JSONResponse(status_code=500, content={"detail": "数据库异常，请稍后再试"})

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(_: Request, exc: RequestValidationError):
        first = exc.errors()[0] if exc.errors() else {}
        loc = ".".join(str(p) for p in first.get("loc", [])[1:]) or "字段"
        msg = first.get("msg", "格式错误")
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content=jsonable_encoder({"detail": f"{loc}: {msg}", "errors": exc.errors()}),
        )
