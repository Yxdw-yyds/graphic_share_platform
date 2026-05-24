import pytest


@pytest.fixture()
def approved_work(client, user_headers, admin_headers):
    res = client.post(
        "/api/works",
        headers=user_headers,
        json={
            "title": "样例",
            "summary": "样例摘要",
            "category": "生活分享",
            "first_chapter_title": "正文",
            "first_chapter_content": "正文",
        },
    )
    wid = res.json()["id"]
    client.post(
        f"/api/works/{wid}/review",
        headers=admin_headers,
        json={"approved": True, "note": ""},
    )
    return wid


def test_comment_then_get(client, user_headers, approved_work):
    res = client.post(
        f"/api/works/{approved_work}/comments",
        headers=user_headers,
        json={"content": "first!"},
    )
    assert res.status_code == 200
    cid = res.json()["id"]

    detail = client.get(f"/api/comments/{cid}")
    assert detail.status_code == 200
    assert detail.json()["content"] == "first!"

    listed = client.get(f"/api/works/{approved_work}/comments").json()
    assert len(listed) == 1


def test_owner_comment_management_returns_tree_without_duplicate_replies(client, user_headers, approved_work):
    parent = client.post(
        f"/api/works/{approved_work}/comments",
        headers=user_headers,
        json={"content": "parent"},
    ).json()
    reply = client.post(
        f"/api/works/{approved_work}/comments",
        headers=user_headers,
        json={"content": "reply", "parent_id": parent["id"]},
    ).json()

    listed = client.get(f"/api/works/{approved_work}/all-comments", headers=user_headers).json()
    assert [comment["id"] for comment in listed] == [parent["id"]]
    assert [comment["id"] for comment in listed[0]["replies"]] == [reply["id"]]
    assert listed[0]["replies"][0]["parent_user"]["id"] == parent["user_id"]


def test_comment_too_long(client, user_headers, approved_work):
    res = client.post(
        f"/api/works/{approved_work}/comments",
        headers=user_headers,
        json={"content": "x" * 600},
    )
    assert res.status_code == 422


def test_like_toggle(client, user_headers, approved_work):
    r1 = client.post(f"/api/works/{approved_work}/like", headers=user_headers).json()
    assert r1 == {"liked": True, "like_count": 1}
    r2 = client.post(f"/api/works/{approved_work}/like", headers=user_headers).json()
    assert r2 == {"liked": False, "like_count": 0}


def test_favorite_toggle(client, user_headers, approved_work):
    r1 = client.post(f"/api/works/{approved_work}/favorite", headers=user_headers).json()
    assert r1["favorited"] is True
    favs = client.get("/api/favorites", headers=user_headers).json()
    assert any(w["id"] == approved_work for w in favs)
    favorite = next(w for w in favs if w["id"] == approved_work)
    assert "password_hash" not in favorite["author"]


def test_report_invalid_reason(client, user_headers, approved_work):
    res = client.post(
        "/api/reports",
        headers=user_headers,
        json={
            "target_type": "work",
            "target_id": approved_work,
            "reason": "瞎说",
        },
    )
    assert res.status_code == 422


def test_report_create_and_handle(client, user_headers, admin_headers, approved_work):
    res = client.post(
        "/api/reports",
        headers=user_headers,
        json={
            "target_type": "work",
            "target_id": approved_work,
            "reason": "垃圾广告",
            "description": "spam",
        },
    )
    assert res.status_code == 200
    rid = res.json()["id"]

    listed = client.get("/api/reports", headers=admin_headers).json()
    report = next(r for r in listed if r["id"] == rid)
    assert "password_hash" not in report["reporter"]

    res = client.post(
        f"/api/reports/{rid}/handle?approved=true", headers=admin_headers
    )
    assert res.status_code == 200
