"""Pytest fixtures: 内存 SQLite + TestClient + 预置 admin/user 账号。

每个测试函数独立 schema，不会污染开发数据库。
"""
import os
import sys
from pathlib import Path

# 关闭演示数据自动注入，避免影响断言
os.environ.setdefault("CREATE_DEMO_DATA", "false")
os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")

# 让 `from backend...` 在不安装包的情况下可用
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.core.security import hash_password
from backend.database import Base, get_db
from backend.main import app
from backend.models import User


@pytest.fixture()
def db_session():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine, autoflush=False, autocommit=False)
    session = Session()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)


@pytest.fixture()
def client(db_session):
    def _override():
        try:
            yield db_session
        finally:
            pass

    app.dependency_overrides[get_db] = _override
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture()
def admin_user(db_session) -> User:
    u = User(
        account="13800000001",
        password_hash=hash_password("Admin@123456"),
        nickname="Admin",
        role="admin",
    )
    db_session.add(u)
    db_session.commit()
    db_session.refresh(u)
    return u


@pytest.fixture()
def normal_user(db_session) -> User:
    u = User(
        account="13800000002",
        password_hash=hash_password("User@123456"),
        nickname="演示用户",
        role="user",
    )
    db_session.add(u)
    db_session.commit()
    db_session.refresh(u)
    return u


def auth_header(client: TestClient, account: str, password: str) -> dict[str, str]:
    res = client.post(
        "/api/auth/login/password", json={"account": account, "password": password}
    )
    assert res.status_code == 200, res.text
    return {"Authorization": f"Bearer {res.json()['access_token']}"}


@pytest.fixture()
def admin_headers(client, admin_user):
    return auth_header(client, admin_user.account, "Admin@123456")


@pytest.fixture()
def user_headers(client, normal_user):
    return auth_header(client, normal_user.account, "User@123456")
