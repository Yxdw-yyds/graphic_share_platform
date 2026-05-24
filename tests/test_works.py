def _create_work(client, headers, title="作品 A"):
    return client.post(
        "/api/works",
        headers=headers,
        json={
            "title": title,
            "summary": "摘要",
            "category": "生活分享",
            "first_chapter_title": "正文",
            "first_chapter_content": "这是正文内容",
        },
    )


def _create_flagged_work(client, headers, title="作品待人工审核"):
    return client.post(
        "/api/works",
        headers=headers,
        json={
            "title": title,
            "summary": "摘要",
            "category": "生活分享",
            "first_chapter_title": "正文",
            "first_chapter_content": "这是正文内容，包含诈骗风险词",
        },
    )


def test_create_then_review_flow(client, user_headers, admin_headers):
    res = _create_work(client, user_headers)
    assert res.status_code == 200
    work_id = res.json()["id"]
    assert res.json()["status"] == "ai_approved"

    listed = client.get("/api/works").json()
    assert any(w["id"] == work_id for w in listed)

    pend = client.get("/api/works/pending", headers=admin_headers).json()
    assert any(w["id"] == work_id for w in pend)

    rev = client.post(
        f"/api/works/{work_id}/review",
        headers=admin_headers,
        json={"approved": True, "note": "OK"},
    )
    assert rev.status_code == 200

    listed2 = client.get("/api/works").json()
    assert any(w["id"] == work_id for w in listed2)


def test_ai_flagged_work_waits_for_manual_review(client, user_headers, admin_headers):
    res = _create_flagged_work(client, user_headers)
    assert res.status_code == 200
    work_id = res.json()["id"]
    assert res.json()["status"] == "pending"
    assert "AI初审未通过" in res.json()["review_note"]

    listed = client.get("/api/works").json()
    assert all(w["id"] != work_id for w in listed)

    pend = client.get("/api/works/pending", headers=admin_headers).json()
    assert any(w["id"] == work_id for w in pend)


def test_admin_rejection_withdraws_ai_published_work(client, user_headers, admin_headers):
    work_id = _create_work(client, user_headers, title="临时发布后撤回").json()["id"]
    assert any(w["id"] == work_id for w in client.get("/api/works").json())

    rev = client.post(
        f"/api/works/{work_id}/review",
        headers=admin_headers,
        json={"approved": False, "note": "内容不适合发布"},
    )
    assert rev.status_code == 200
    assert rev.json()["status"] == "rejected"
    assert rev.json()["review_note"] == "内容不适合发布"
    assert all(w["id"] != work_id for w in client.get("/api/works").json())


def test_pagination_wrapper(client, user_headers, admin_headers):
    for i in range(5):
        rid = _create_work(client, user_headers, title=f"作品-{i}").json()["id"]
        client.post(
            f"/api/works/{rid}/review",
            headers=admin_headers,
            json={"approved": True, "note": ""},
        )

    page = client.get("/api/works?page=1&size=2").json()
    assert page["total"] >= 5
    assert page["page"] == 1
    assert page["size"] == 2
    assert len(page["items"]) == 2
    assert page["pages"] >= 3
    assert all("password_hash" not in w["author"] for w in page["items"])


def test_work_lists_do_not_expose_password_hash(client, user_headers, admin_headers):
    work_id = _create_work(client, user_headers, title="安全序列化").json()["id"]
    client.post(
        f"/api/works/{work_id}/review",
        headers=admin_headers,
        json={"approved": True, "note": ""},
    )

    listed = client.get("/api/works").json()
    listed_item = next(w for w in listed if w["id"] == work_id)
    assert "password_hash" not in listed_item["author"]

    pending_id = _create_work(client, user_headers, title="待审核安全序列化").json()["id"]
    pending = client.get("/api/works/pending", headers=admin_headers).json()
    pending_item = next(w for w in pending if w["id"] == pending_id)
    assert "password_hash" not in pending_item["author"]


def test_create_work_validation(client, user_headers):
    res = client.post(
        "/api/works",
        headers=user_headers,
        json={"title": "", "first_chapter_content": "x"},
    )
    assert res.status_code == 422


def test_delete_requires_owner(client, user_headers, admin_headers):
    work_id = _create_work(client, user_headers).json()["id"]
    # admin 也允许删
    res = client.delete(f"/api/works/{work_id}", headers=admin_headers)
    assert res.status_code == 200


def test_unauthorized_create(client):
    res = _create_work(client, {})
    assert res.status_code == 401
