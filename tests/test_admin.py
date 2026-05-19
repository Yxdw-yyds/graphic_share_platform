def test_stats_admin_only(client, admin_headers, user_headers):
    forbidden = client.get("/api/admin/stats", headers=user_headers)
    assert forbidden.status_code == 403

    res = client.get("/api/admin/stats", headers=admin_headers)
    assert res.status_code == 200
    body = res.json()
    for key in ("pending_works", "pending_reports"):
        assert key in body


def test_list_users_admin_only(client, admin_headers, user_headers):
    forbidden = client.get("/api/users", headers=user_headers)
    assert forbidden.status_code == 403

    res = client.get("/api/users", headers=admin_headers)
    assert res.status_code == 200
    assert len(res.json()) >= 2  # admin + user


def test_users_pagination(client, admin_headers):
    res = client.get("/api/users?page=1&size=1", headers=admin_headers).json()
    assert res["page"] == 1
    assert res["size"] == 1
    assert len(res["items"]) == 1


def test_validation_returns_chinese_detail(client):
    res = client.post(
        "/api/auth/register",
        json={"account": "x", "password": "1", "code": ""},
    )
    assert res.status_code == 422
    assert "detail" in res.json()
