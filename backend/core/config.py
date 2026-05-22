from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Graphic Share Platform"
    secret_key: str = "dev-secret-key"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 1440
    database_url: str = "sqlite:///./graphic_share_platform.db"
    upload_dir: str = "uploads"
    log_dir: str = "logs"
    create_demo_data: bool = True
    debug: bool = True
    cors_origins: str = "*"  # 逗号分隔，例：http://localhost:5173,http://127.0.0.1:8001
    smtp_host: str = "smtp.qq.com"
    smtp_port: int = 465
    smtp_username: str = "2412030860@qq.com"
    smtp_password: str = ""
    smtp_sender: str = "2412030860@qq.com"
    smtp_use_ssl: bool = True

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    @property
    def cors_origin_list(self) -> list[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
