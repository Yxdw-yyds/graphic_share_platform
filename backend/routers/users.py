from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from backend.core.security import hash_password, verify_password
from backend.database import get_db
from backend.deps import get_current_user, require_admin
from backend.models import Comment, Follow, User, Work
from backend.schemas.common import CommentOut, FollowOut, UserOut, WorkOut
from backend.schemas.requests import AdminPasswordResetIn, AdminUserUpdateIn, ChangePasswordIn, UserUpdateIn
from backend.utils.pagination import PageParams, page_params, paginate

router = APIRouter(prefix="/api/users", tags=["users"])


def serialize_page(result, schema):
    if isinstance(result, dict):
        result["items"] = [schema.model_validate(item) for item in result["items"]]
        return result
    return [schema.model_validate(item) for item in result]


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
    return paginate(query, pp)


@router.get("/me/comments")
def my_comments(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    pp: PageParams = Depends(page_params),
):
    query = db.query(Comment).filter(Comment.user_id == user.id).order_by(Comment.created_at.desc())
    return paginate(query, pp)


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
        .filter(Work.author_id == user_id, Work.status == "approved")
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
    return paginate(query.order_by(User.created_at.desc()), pp)


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
