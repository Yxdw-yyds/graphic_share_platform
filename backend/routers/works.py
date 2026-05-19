from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlalchemy import func
from sqlalchemy.orm import Session, joinedload

from backend.core.config import get_settings
from backend.database import get_db
from backend.deps import get_current_user, get_optional_user, require_admin
from backend.models import Chapter, Favorite, Like, User, Work
from backend.models import Comment
from backend.schemas.common import ChapterOut, WorkOut
from backend.schemas.requests import ChapterCreateIn, ChapterUpdateIn, ReviewIn, WorkCreateIn, WorkUpdateIn
from backend.services.metrics import refresh_work_metrics
from backend.utils.pagination import PageParams, page_params, paginate

router = APIRouter(prefix="/api/works", tags=["works"])


def get_work_or_404(db: Session, work_id: int) -> Work:
    work = db.query(Work).filter(Work.id == work_id).first()
    if not work:
        raise HTTPException(status_code=404, detail="作品不存在")
    return work


def can_view(work: Work, user: User | None) -> bool:
    return work.status == "approved" or (user and (user.role == "admin" or user.id == work.author_id))


@router.post("/upload-image")
async def upload_image(file: UploadFile = File(...), _: User = Depends(get_current_user)):
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
    query = db.query(Work).options(joinedload(Work.author)).filter(Work.status == "approved")
    if keyword:
        query = query.filter((Work.title.contains(keyword)) | (Work.summary.contains(keyword)))
    if category:
        query = query.filter(Work.category == category)
    order = Work.heat.desc() if hot else Work.created_at.desc()
    return paginate(query.order_by(order), pp)


@router.get("/pending")
def pending_works(
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
    pp: PageParams = Depends(page_params),
):
    query = (
        db.query(Work)
        .options(joinedload(Work.author))
        .filter(Work.status == "pending")
        .order_by(Work.created_at.desc())
    )
    return paginate(query, pp)


@router.get("/trending")
def trending_topics(db: Session = Depends(get_db)):
    rows = (
        db.query(
            Work.category,
            func.count(Work.id).label("post_count"),
            func.coalesce(func.sum(Work.heat), 0).label("heat_score"),
        )
        .filter(Work.status == "approved")
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
    work = Work(
        author_id=user.id,
        title=payload.title,
        summary=payload.summary or payload.first_chapter_content[:120],
        category=payload.category.replace("#", ""),
        cover_image=payload.cover_image,
        status="pending",
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
            status="pending",
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
    if work.status == "approved":
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
        first_chapter.status = "pending"
    work.status = "pending"
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
    work.status = "approved" if payload.approved else "rejected"
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
        query = query.filter(Chapter.status == "approved")
    return query.order_by(Chapter.sort_order).all()


@router.get("/{work_id}/all-comments")
def list_work_comments_for_owner(
    work_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    work = get_work_or_404(db, work_id)
    if user.role != "admin" and work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权查看该作品评论")
    return (
        db.query(Comment)
        .options(joinedload(Comment.user))
        .filter(Comment.work_id == work_id, Comment.status == "visible")
        .order_by(Comment.created_at.desc())
        .all()
    )


@router.post("/{work_id}/chapters", response_model=ChapterOut)
def create_chapter(work_id: int, payload: ChapterCreateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = get_work_or_404(db, work_id)
    if user.role != "admin" and work.author_id != user.id:
        raise HTTPException(status_code=403, detail="无权新增章节")
    chapter = Chapter(work_id=work_id, **payload.model_dump(), status="pending")
    work.status = "pending"
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
    chapter.status = "pending"
    chapter.work.status = "pending"
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
    work.status = "pending"
    db.commit()
    return {"message": "章节已删除，作品等待重新审核"}
