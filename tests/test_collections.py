def _create_work(client, headers):
    res = client.post(
        "/api/works",
        headers=headers,
        json={
            "title": "合集动态",
            "category": "生活分享",
            "first_chapter_content": "合集正文",
        },
    )
    assert res.status_code == 200, res.text
    return res.json()["id"]


def test_collection_add_and_remove_work(client, user_headers):
    work_id = _create_work(client, user_headers)

    created = client.post(
        "/api/users/me/collections",
        headers=user_headers,
        json={"title": "旅行合集", "description": "周末记录"},
    )
    assert created.status_code == 200, created.text
    collection_id = created.json()["id"]

    added = client.post(
        f"/api/users/me/collections/{collection_id}/items",
        headers=user_headers,
        json={"work_id": work_id, "sort_order": 1},
    )
    assert added.status_code == 200, added.text
    item_id = added.json()["id"]

    listed = client.get("/api/users/me/collections", headers=user_headers)
    assert listed.status_code == 200
    body = listed.json()
    collection = next(item for item in body if item["id"] == collection_id)
    assert collection["items"][0]["work"]["id"] == work_id

    moved = client.patch(
        f"/api/users/me/collection-items/{item_id}",
        headers=user_headers,
        json={"sort_order": 2},
    )
    assert moved.status_code == 200
    assert moved.json()["sort_order"] == 2

    removed = client.delete(f"/api/users/me/collection-items/{item_id}", headers=user_headers)
    assert removed.status_code == 200


def test_public_collections_and_work_collection_links(client, user_headers, admin_headers):
    work_id = _create_work(client, user_headers)
    client.post(
        f"/api/works/{work_id}/review",
        headers=admin_headers,
        json={"approved": True, "note": ""},
    )

    created = client.post(
        "/api/users/me/collections",
        headers=user_headers,
        json={"title": "公开合集", "description": "可以访问"},
    ).json()
    client.post(
        f"/api/users/me/collections/{created['id']}/items",
        headers=user_headers,
        json={"work_id": work_id, "sort_order": 1},
    )

    me = client.get("/api/auth/me", headers=user_headers).json()
    public_list = client.get(f"/api/users/{me['id']}/collections").json()
    public_collection = next(item for item in public_list if item["id"] == created["id"])
    assert public_collection["items"][0]["work"]["id"] == work_id
    assert public_collection["items"][0]["work"]["status"] == "approved"

    detail = client.get(f"/api/users/{me['id']}/collections/{created['id']}").json()
    assert detail["id"] == created["id"]
    assert detail["items"][0]["work"]["id"] == work_id

    linked = client.get(f"/api/works/{work_id}/collections").json()
    assert any(item["id"] == created["id"] for item in linked)
