from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from backend.core.security import hash_password, verify_password
from backend.database import get_db
from backend.deps import get_current_user, require_admin
from sqlalchemy.exc import IntegrityError

from backend.models import Collection, CollectionItem, Comment, Follow, User, Work
from backend.schemas.common import CollectionItemOut, CollectionOut, CommentOut, FollowOut, UserOut, WorkOut
from backend.schemas.requests import (
    AdminPasswordResetIn,
    AdminUserUpdateIn,
    ChangePasswordIn,
    CollectionCreateIn,
    CollectionItemCreateIn,
    CollectionItemUpdateIn,
    CollectionUpdateIn,
    UserUpdateIn,
)
from backend.services.ai_review import PUBLIC_WORK_STATUSES
from backend.utils.pagination import PageParams, page_params, paginate, serialize_page

router = APIRouter(prefix="/api/users", tags=["users"])


@router.get("/me", response_model=UserOut)
def get_profile(user: User = Depends(get_current_user)):
    return user


@router.patch("/me", response_model=UserOut)
def update_profile(payload: UserUpdateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(user, key, value)
    db.commit()
    db.refresh(user)
    return user


@router.post("/me/change-password")
def change_password(payload: ChangePasswordIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if not verify_password(payload.old_password, user.password_hash):
        raise HTTPException(status_code=400, detail="原密码错误")
    user.password_hash = hash_password(payload.new_password)
    db.commit()
    return {"message": "密码已修改"}


@router.delete("/me")
def deactivate_me(db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    user.status = "deleted"
    db.commit()
    return {"message": "账号已注销"}


@router.get("/me/works")
def my_works(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    pp: PageParams = Depends(page_params),
):
    query = db.query(Work).filter(Work.author_id == user.id).order_by(Work.created_at.desc())
    return serialize_page(paginate(query, pp), WorkOut)


@router.get("/me/comments")
def my_comments(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    pp: PageParams = Depends(page_params),
):
    query = db.query(Comment).filter(Comment.user_id == user.id).order_by(Comment.created_at.desc())
    return serialize_page(paginate(query, pp), CommentOut)


@router.get("/me/follows")
def my_follows(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    pp: PageParams = Depends(page_params),
):
    query = (
        db.query(Follow)
        .options(joinedload(Follow.followed))
        .filter(Follow.follower_id == user.id)
        .order_by(Follow.created_at.desc())
    )
    return serialize_page(paginate(query, pp), FollowOut)


@router.get("/me/collections", response_model=list[CollectionOut])
def my_collections(db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    return (
        db.query(Collection)
        .options(joinedload(Collection.items).joinedload(CollectionItem.work).joinedload(Work.author))
        .filter(Collection.user_id == user.id)
        .order_by(Collection.updated_at.desc(), Collection.id.desc())
        .all()
    )


@router.post("/me/collections", response_model=CollectionOut)
def create_collection(payload: CollectionCreateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    collection = Collection(user_id=user.id, **payload.model_dump())
    db.add(collection)
    db.commit()
    db.refresh(collection)
    return collection


def get_owned_collection(db: Session, collection_id: int, user: User) -> Collection:
    collection = (
        db.query(Collection)
        .options(joinedload(Collection.items).joinedload(CollectionItem.work).joinedload(Work.author))
        .filter(Collection.id == collection_id, Collection.user_id == user.id)
        .first()
    )
    if not collection:
        raise HTTPException(status_code=404, detail="合集不存在")
    return collection


@router.patch("/me/collections/{collection_id}", response_model=CollectionOut)
def update_collection(
    collection_id: int,
    payload: CollectionUpdateIn,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    collection = get_owned_collection(db, collection_id, user)
    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(collection, key, value)
    db.commit()
    db.refresh(collection)
    return collection


@router.delete("/me/collections/{collection_id}")
def delete_collection(collection_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    collection = get_owned_collection(db, collection_id, user)
    db.delete(collection)
    db.commit()
    return {"message": "合集已删除"}


@router.post("/me/collections/{collection_id}/items", response_model=CollectionItemOut)
def add_collection_item(
    collection_id: int,
    payload: CollectionItemCreateIn,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    collection = get_owned_collection(db, collection_id, user)
    work = db.query(Work).filter(Work.id == payload.work_id, Work.author_id == user.id).first()
    if not work:
        raise HTTPException(status_code=404, detail="动态不存在")
    sort_order = payload.sort_order or (len(collection.items) + 1)
    item = CollectionItem(collection_id=collection.id, work_id=work.id, sort_order=sort_order)
    db.add(item)
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(status_code=400, detail="该动态已在合集里") from exc
    db.refresh(item)
    return item


@router.patch("/me/collection-items/{item_id}", response_model=CollectionItemOut)
def update_collection_item(
    item_id: int,
    payload: CollectionItemUpdateIn,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    item = (
        db.query(CollectionItem)
        .join(Collection)
        .filter(CollectionItem.id == item_id, Collection.user_id == user.id)
        .first()
    )
    if not item:
        raise HTTPException(status_code=404, detail="合集内容不存在")
    item.sort_order = payload.sort_order
    db.commit()
    db.refresh(item)
    return item


@router.delete("/me/collection-items/{item_id}")
def delete_collection_item(item_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    item = (
        db.query(CollectionItem)
        .join(Collection)
        .filter(CollectionItem.id == item_id, Collection.user_id == user.id)
        .first()
    )
    if not item:
        raise HTTPException(status_code=404, detail="合集内容不存在")
    db.delete(item)
    db.commit()
    return {"message": "已从合集中移除"}


def public_collection_query(db: Session, user_id: int):
    return (
        db.query(Collection)
        .options(joinedload(Collection.items).joinedload(CollectionItem.work).joinedload(Work.author))
        .filter(Collection.user_id == user_id)
        .order_by(Collection.updated_at.desc(), Collection.id.desc())
    )


def public_collection_out(collection: Collection) -> dict:
    data = CollectionOut.model_validate(collection).model_dump(mode="json")
    data["items"] = [
        item
        for item in data.get("items", [])
        if item.get("work") and item["work"].get("status") in PUBLIC_WORK_STATUSES
    ]
    return data


@router.get("/{user_id}/collections")
def user_public_collections(user_id: int, db: Session = Depends(get_db)):
    target = db.query(User).filter(User.id == user_id, User.status == "active").first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    collections = public_collection_query(db, user_id).all()
    return [public_collection_out(collection) for collection in collections]


@router.get("/{user_id}/collections/{collection_id}")
def user_public_collection_detail(user_id: int, collection_id: int, db: Session = Depends(get_db)):
    target = db.query(User).filter(User.id == user_id, User.status == "active").first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    collection = public_collection_query(db, user_id).filter(Collection.id == collection_id).first()
    if not collection:
        raise HTTPException(status_code=404, detail="合集不存在")
    return public_collection_out(collection)


@router.get("/{user_id}", response_model=UserOut)
def get_user_home(user_id: int, db: Session = Depends(get_db)):
    target = db.query(User).filter(User.id == user_id, User.status == "active").first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    return target


@router.get("/{user_id}/works")
def user_public_works(
    user_id: int,
    db: Session = Depends(get_db),
    pp: PageParams = Depends(page_params),
):
    target = db.query(User).filter(User.id == user_id, User.status == "active").first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    query = (
        db.query(Work)
        .options(joinedload(Work.author))
        .filter(Work.author_id == user_id, Work.status.in_(PUBLIC_WORK_STATUSES))
        .order_by(Work.created_at.desc())
    )
    return serialize_page(paginate(query, pp), WorkOut)


@router.post("/{user_id}/follow")
def toggle_follow(user_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    target = db.query(User).filter(User.id == user_id, User.status == "active").first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    if target.id == user.id:
        raise HTTPException(status_code=400, detail="不能关注自己")
    existing = db.query(Follow).filter(Follow.follower_id == user.id, Follow.followed_id == target.id).first()
    followed = existing is None
    if existing:
        db.delete(existing)
    else:
        db.add(Follow(follower_id=user.id, followed_id=target.id))
    db.commit()
    return {"followed": followed}


@router.get("")
def list_users(
    keyword: str | None = None,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
    pp: PageParams = Depends(page_params),
):
    query = db.query(User)
    if keyword:
        query = query.filter((User.account.contains(keyword)) | (User.nickname.contains(keyword)))
    return serialize_page(paginate(query.order_by(User.created_at.desc()), pp), UserOut)


@router.patch("/{user_id}/status")
def update_user_status(user_id: int, status: str, db: Session = Depends(get_db), _: User = Depends(require_admin)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    user.status = status
    db.commit()
    return {"message": "用户状态已更新"}


@router.patch("/{user_id}", response_model=UserOut)
def admin_update_user(
    user_id: int,
    payload: AdminUserUpdateIn,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(user, key, value)
    db.commit()
    db.refresh(user)
    return user


@router.post("/{user_id}/reset-password")
def admin_reset_password(
    user_id: int,
    payload: AdminPasswordResetIn,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    user.password_hash = hash_password(payload.new_password)
    db.commit()
    return {"message": "密码已重置"}
