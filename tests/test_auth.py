def get_code(client, account: str, purpose: str) -> str:
    res = client.post("/api/auth/send-code", json={"account": account, "purpose": purpose})
    assert res.status_code == 200
    return res.json()["code"]


def test_register_login_flow(client):
    account = "user1@test.com"
    code = get_code(client, account, "register")

    res = client.post(
        "/api/auth/register",
        json={"account": account, "password": "Passw0rd!", "code": code, "nickname": "U1"},
    )
    assert res.status_code == 200
    token = res.json()["access_token"]
    assert token

    res = client.post(
        "/api/auth/login/password", json={"account": account, "password": "Passw0rd!"}
    )
    assert res.status_code == 200


def test_login_wrong_password(client, normal_user):
    res = client.post(
        "/api/auth/login/password",
        json={"account": normal_user.account, "password": "wrong-pwd"},
    )
    assert res.status_code == 400
    assert "账号或密码错误" in res.json()["detail"]


def test_register_invalid_account(client):
    res = client.post(
        "/api/auth/register",
        json={"account": "not-email", "password": "Passw0rd!", "code": "123456"},
    )
    assert res.status_code == 422


def test_register_short_password(client):
    code = get_code(client, "u2@test.com", "register")
    res = client.post(
        "/api/auth/register",
        json={"account": "u2@test.com", "password": "short", "code": code},
    )
    assert res.status_code == 422


def test_code_consumed_once(client):
    account = "u3@test.com"
    code = get_code(client, account, "register")
    r1 = client.post(
        "/api/auth/register",
        json={"account": account, "password": "Passw0rd!", "code": code},
    )
    assert r1.status_code == 200
    r2 = client.post(
        "/api/auth/register",
        json={"account": "u4@test.com", "password": "Passw0rd!", "code": code},
    )
    assert r2.status_code == 400


def test_me(client, user_headers):
    res = client.get("/api/auth/me", headers=user_headers)
    assert res.status_code == 200
    assert res.json()["account"] == "13800000002"
