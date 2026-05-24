from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlalchemy import func
from sqlalchemy.orm import Session, joinedload

from backend.core.config import get_settings
from backend.database import get_db
from backend.deps import get_current_user, get_optional_user, require_admin
from backend.models import Chapter, Collection, CollectionItem, Favorite, Like, User, Work
from backend.models import Comment
from backend.models.entities import now_utc
from backend.schemas.common import ChapterOut, CollectionOut, WorkOut
from backend.schemas.common import CommentOut, UserOut
from backend.schemas.requests import ChapterCreateIn, ChapterUpdateIn, ReviewIn, WorkCreateIn, WorkUpdateIn
from backend.services.ai_review import AI_APPROVED, APPROVED, PENDING, PUBLIC_WORK_STATUSES, REJECTED, REVIEW_QUEUE_STATUSES, simple_ai_review
from backend.services.metrics import refresh_work_metrics
from backend.utils.pagination import PageParams, page_params, paginate, serialize_page

router = APIRouter(prefix="/api/works", tags=["works"])


def get_work_or_404(db: Session, work_id: int) -> Work:
    work = db.query(Work).filter(Work.id == work_id).first()
    if not work:
        raise HTTPException(status_code=404, detail="作品不存在")
    return work


def can_view(work: Work, user: User | None) -> bool:
    return work.status in PUBLIC_WORK_STATUSES or (user and (user.role == "admin" or user.id == work.author_id))


def comment_out(comment: Comment, parent_by_id: dict[int, Comment] | None = None) -> dict:
    data = CommentOut.model_validate(comment).model_dump(mode="json")
    parent = parent_by_id.get(comment.parent_id) if parent_by_id and comment.parent_id else None
    if parent and parent.user:
        data["parent_user"] = UserOut.model_validate(parent.user).model_dump(mode="json")
    return data


@router.post("/upload-image")
async def upload_image(file: UploadFile = File(...), _: User = Depends(get_current_user)):
    return await save_work_image(file)


@router.post("/upload-images")
async def upload_images(files: list[UploadFile] = File(...), _: User = Depends(get_current_user)):
    if not files:
        raise HTTPException(status_code=400, detail="请至少选择 1 张图片")
    if len(files) > 4:
        raise HTTPException(status_code=400, detail="最多只能上传 4 张图片")
    urls = []
    for file in files:
        uploaded = await save_work_image(file)
        urls.append(uploaded["url"])
    return {"urls": urls, "images": urls}


async def save_work_image(file: UploadFile):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="只能上传图片文件")
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png", ".gif", ".webp"}:
        suffix = ".jpg"
    settings = get_settings()
    upload_dir = Path(settings.upload_dir) / "works"
    upload_dir.mkdir(parents=True, exist_ok=True)
    filename = f"{uuid4().hex}{suffix}"
    path = upload_dir / filename
    size = 0
    with path.open("wb") as out:
        while chunk := await file.read(1024 * 1024):
            size += len(chunk)
            if size > 5 * 1024 * 1024:
                path.unlink(missing_ok=True)
                raise HTTPException(status_code=400, detail="图片不能超过 5MB")
            out.write(chunk)
    return {"url": f"/uploads/works/{filename}"}


@router.get("")
def list_works(
    keyword: str | None = None,
    category: str | None = None,
    hot: bool = False,
    db: Session = Depends(get_db),
    pp: PageParams = Depends(page_params),
):
    query = db.query(Work).options(joinedload(Work.author)).filter(Work.status.in_(PUBLIC_WORK_STATUSES))
    if keyword:
        query = query.filter((Work.title.contains(keyword)) | (Work.summary.contains(keyword)))
    if category:
        query = query.filter(Work.category == category)
    order = Work.heat.desc() if hot else Work.created_at.desc()
    return serialize_page(paginate(query.order_by(order), pp), WorkOut)


@router.get("/pending")
def pending_works(
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
    pp: PageParams = Depends(page_params),
):
    query = (
        db.query(Work)
        .options(joinedload(Work.author))
        .filter(Work.status.in_(REVIEW_QUEUE_STATUSES))
        .order_by(Work.updated_at.desc(), Work.created_at.desc())
    )
    return serialize_page(paginate(query, pp), WorkOut)


@router.get("/trending")
def trending_topics(db: Session = Depends(get_db)):
    rows = (
        db.query(
            Work.category,
            func.count(Work.id).label("post_count"),
            func.coalesce(func.sum(Work.heat), 0).label("heat_score"),
        )
        .filter(Work.status.in_(PUBLIC_WORK_STATUSES))
        .group_by(Work.category)
        .order_by(func.coalesce(func.sum(Work.heat), 0).desc(), func.count(Work.id).desc())
        .limit(8)
        .all()
    )
    return [
        {
            "category": row.category,
            "topic": f"#{row.category}",
            "post_count": int(row.post_count or 0),
            "heat_score": float(row.heat_score or 0),
        }
        for row in rows
    ]


@router.post("", response_model=WorkOut)
def create_work(payload: WorkCreateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    category = payload.category.replace("#", "")
    summary = payload.summary or payload.first_chapter_content[:120]
    ai_result = simple_ai_review(payload.title, summary, payload.first_chapter_content, category)
    status = AI_APPROVED if ai_result.approved else PENDING
    work = Work(
        author_id=user.id,
        title=payload.title,
        summary=summary,
        category=category,
        cover_image=payload.cover_image,
        status=status,
        review_note=ai_result.note,
    )
    db.add(work)
    db.flush()
    db.add(
        Chapter(
            work_id=work.id,
            title=payload.first_chapter_title,
            content=payload.first_chapter_content,
            image_url=payload.cover_image,
            sort_order=1,
            status=status,
        )
    )
    db.commit()
    db.refresh(work)
    return work


@router.get("/{work_id}", response_model=WorkOut)
def get_work(work_id: int, db: Session = Depends(get_db), user: User | None = Depends(get_optional_user)):
    work = db.query(Work).options(joinedload(Work.author)).filter(Work.id == work_id).first()
    if not work or not can_view(work, user):
        raise HTTPException(status_code=404, detail="作品不存在")
    if work.status in PUBLIC_WORK_STATUSES:
        work.view_count += 1
        refresh_work_metrics(db, work)
        db.commit()
        db.refresh(work)
    return work


@router.patch("/{work_id}", response_model=WorkOut)
def update_work(work_id: int, payload: WorkUpdateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = get_work_or_404(db, work_id)
    if user.role != "admin" and work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权修改该作品")
    updates = payload.model_dump(exclude_unset=True)
    if "category" in updates and updates["category"]:
        updates["category"] = updates["category"].replace("#", "")
    for key, value in updates.items():
        setattr(work, key, value)
    first_chapter = (
        db.query(Chapter)
        .filter(Chapter.work_id == work.id)
        .order_by(Chapter.sort_order.asc(), Chapter.id.asc())
        .first()
    )
    if first_chapter:
        if "summary" in updates and updates["summary"]:
            first_chapter.content = updates["summary"]
        if "cover_image" in updates:
            first_chapter.image_url = updates["cover_image"]
    first_chapter_content = first_chapter.content if first_chapter else (work.summary or work.title)
    ai_result = simple_ai_review(work.title, work.summary, first_chapter_content, work.category)
    work.status = AI_APPROVED if ai_result.approved else PENDING
    work.review_note = ai_result.note
    if first_chapter:
        first_chapter.status = work.status
        first_chapter.updated_at = now_utc()
    work.updated_at = now_utc()
    db.commit()
    db.refresh(work)
    return work


@router.delete("/{work_id}")
def delete_work(work_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = get_work_or_404(db, work_id)
    if user.role != "admin" and work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权删除该作品")
    db.delete(work)
    db.commit()
    return {"message": "作品已删除"}


@router.post("/{work_id}/review", response_model=WorkOut)
def review_work(work_id: int, payload: ReviewIn, db: Session = Depends(get_db), _: User = Depends(require_admin)):
    work = get_work_or_404(db, work_id)
    work.status = APPROVED if payload.approved else REJECTED
    work.review_note = payload.note
    for chapter in work.chapters:
        chapter.status = work.status
    refresh_work_metrics(db, work)
    db.commit()
    db.refresh(work)
    return work


@router.post("/{work_id}/like")
def toggle_like(work_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = get_work_or_404(db, work_id)
    existing = db.query(Like).filter(Like.user_id == user.id, Like.work_id == work.id).first()
    liked = existing is None
    if existing:
        db.delete(existing)
    else:
        db.add(Like(user_id=user.id, work_id=work.id))
    db.flush()
    refresh_work_metrics(db, work)
    db.commit()
    return {"liked": liked, "like_count": work.like_count}


@router.post("/{work_id}/favorite")
def toggle_favorite(work_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = get_work_or_404(db, work_id)
    existing = db.query(Favorite).filter(Favorite.user_id == user.id, Favorite.work_id == work.id).first()
    favorited = existing is None
    if existing:
        db.delete(existing)
    else:
        db.add(Favorite(user_id=user.id, work_id=work.id))
    db.flush()
    refresh_work_metrics(db, work)
    db.commit()
    return {"favorited": favorited, "favorite_count": work.favorite_count}


@router.get("/{work_id}/chapters", response_model=list[ChapterOut])
def list_chapters(work_id: int, db: Session = Depends(get_db), user: User | None = Depends(get_optional_user)):
    work = get_work_or_404(db, work_id)
    if not can_view(work, user):
        raise HTTPException(status_code=404, detail="作品不存在")
    query = db.query(Chapter).filter(Chapter.work_id == work_id)
    if not user or (user.role != "admin" and user.id != work.author_id):
        query = query.filter(Chapter.status.in_(PUBLIC_WORK_STATUSES))
    return query.order_by(Chapter.sort_order).all()


def public_collection_out(collection: Collection) -> dict:
    data = CollectionOut.model_validate(collection).model_dump(mode="json")
    data["items"] = [
        item
        for item in data.get("items", [])
        if item.get("work") and item["work"].get("status") in PUBLIC_WORK_STATUSES
    ]
    return data


@router.get("/{work_id}/collections")
def list_work_collections(work_id: int, db: Session = Depends(get_db), user: User | None = Depends(get_optional_user)):
    work = get_work_or_404(db, work_id)
    if not can_view(work, user):
        raise HTTPException(status_code=404, detail="作品不存在")
    query = (
        db.query(Collection)
        .join(CollectionItem)
        .options(joinedload(Collection.items).joinedload(CollectionItem.work).joinedload(Work.author))
        .filter(CollectionItem.work_id == work_id)
        .order_by(Collection.updated_at.desc(), Collection.id.desc())
    )
    collections = query.all()
    if user and (user.role == "admin" or user.id == work.author_id):
        return [CollectionOut.model_validate(collection).model_dump(mode="json") for collection in collections]
    return [public_collection_out(collection) for collection in collections if collection.user_id == work.author_id]


@router.get("/{work_id}/all-comments")
def list_work_comments_for_owner(
    work_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    work = get_work_or_404(db, work_id)
    if user.role != "admin" and work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权查看该作品评论")
    parents = (
        db.query(Comment)
        .options(joinedload(Comment.user))
        .filter(Comment.work_id == work_id, Comment.status == "visible", Comment.parent_id.is_(None))
        .order_by(Comment.created_at.asc())
        .all()
    )
    parent_ids = [comment.id for comment in parents]
    replies_by_parent: dict[int, list[Comment]] = {comment_id: [] for comment_id in parent_ids}
    if parent_ids:
        replies = (
            db.query(Comment)
            .options(joinedload(Comment.user))
            .filter(Comment.work_id == work_id, Comment.status == "visible", Comment.parent_id.in_(parent_ids))
            .order_by(Comment.created_at.asc())
            .all()
        )
        for reply in replies:
            if reply.parent_id in replies_by_parent:
                replies_by_parent[reply.parent_id].append(reply)
    return [
        {
            **comment_out(comment),
            "replies": [comment_out(reply, {comment.id: comment}) for reply in replies_by_parent.get(comment.id, [])],
        }
        for comment in parents
    ]


@router.post("/{work_id}/chapters", response_model=ChapterOut)
def create_chapter(work_id: int, payload: ChapterCreateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = get_work_or_404(db, work_id)
    if user.role != "admin" and work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权新增章节")
    chapter = Chapter(work_id=work_id, **payload.model_dump(), status=PENDING)
    work.status = PENDING
    db.add(chapter)
    db.commit()
    db.refresh(chapter)
    return chapter


@router.patch("/chapters/{chapter_id}", response_model=ChapterOut)
def update_chapter(chapter_id: int, payload: ChapterUpdateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    chapter = db.query(Chapter).filter(Chapter.id == chapter_id).first()
    if not chapter:
        raise HTTPException(status_code=404, detail="章节不存在")
    if user.role != "admin" and chapter.work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权修改章节")
    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(chapter, key, value)
    chapter.status = PENDING
    chapter.work.status = PENDING
    db.commit()
    db.refresh(chapter)
    return chapter


@router.delete("/chapters/{chapter_id}")
def delete_chapter(chapter_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    chapter = db.query(Chapter).filter(Chapter.id == chapter_id).first()
    if not chapter:
        raise HTTPException(status_code=404, detail="章节不存在")
    if user.role != "admin" and chapter.work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权删除章节")
    work = chapter.work
    db.delete(chapter)
    work.status = PENDING
    db.commit()
    return {"message": "章节已删除，作品等待重新审核"}
