from datetime import date, datetime, time, timezone

from fastapi import APIRouter, Depends
from fastapi import HTTPException
from sqlalchemy.orm import Session, joinedload

from backend.database import get_db
from backend.deps import require_admin
from backend.models import Chapter, Comment, Report, User, Work
from backend.schemas.common import ChapterOut, CommentOut, UserOut, WorkOut
from backend.services.ai_review import REVIEW_QUEUE_STATUSES

router = APIRouter(prefix="/api/admin", tags=["admin"])


def comment_out(comment: Comment, parent_by_id: dict[int, Comment] | None = None) -> dict:
    data = CommentOut.model_validate(comment).model_dump(mode="json")
    parent = parent_by_id.get(comment.parent_id) if parent_by_id and comment.parent_id else None
    if parent and parent.user:
        data["parent_user"] = UserOut.model_validate(parent.user).model_dump(mode="json")
    return data


@router.get("/stats")
def stats(db: Session = Depends(get_db), _: User = Depends(require_admin)):
    start = datetime.combine(date.today(), time.min, tzinfo=timezone.utc)
    return {
        "new_users_today": db.query(User).filter(User.created_at >= start).count(),
        "works_today": db.query(Work).filter(Work.created_at >= start).count(),
        "pending_works": db.query(Work).filter(Work.status.in_(REVIEW_QUEUE_STATUSES)).count(),
        "pending_reports": db.query(Report).filter(Report.status == "pending").count(),
        "total_users": db.query(User).count(),
        "total_works": db.query(Work).count(),
    }


@router.get("/works/{work_id}", response_model=WorkOut)
def admin_work_detail(work_id: int, db: Session = Depends(get_db), _: User = Depends(require_admin)):
    work = db.query(Work).options(joinedload(Work.author)).filter(Work.id == work_id).first()
    if not work:
        raise HTTPException(status_code=404, detail="作品不存在")
    return work


@router.get("/reports/target")
def admin_report_target_detail(
    target_type: str,
    target_id: int,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
):
    if target_type == "work":
        work = db.query(Work).options(joinedload(Work.author)).filter(Work.id == target_id).first()
        if not work:
            raise HTTPException(status_code=404, detail="帖子不存在")
    elif target_type == "comment":
        comment = db.query(Comment).filter(Comment.id == target_id).first()
        if not comment:
            raise HTTPException(status_code=404, detail="评论不存在")
        work = db.query(Work).options(joinedload(Work.author)).filter(Work.id == comment.work_id).first()
        if not work:
            raise HTTPException(status_code=404, detail="评论所属帖子不存在")
    else:
        raise HTTPException(status_code=400, detail="不支持的举报类型")

    comments = (
        db.query(Comment)
        .options(joinedload(Comment.user))
        .filter(Comment.work_id == work.id)
        .order_by(Comment.created_at.asc())
        .all()
    )
    parent_by_id = {comment.id: comment for comment in comments}
    return {
        "target_type": target_type,
        "target_id": target_id,
        "work": WorkOut.model_validate(work).model_dump(mode="json"),
        "comments": [comment_out(comment, parent_by_id) for comment in comments],
    }


@router.get("/works/{work_id}/chapters", response_model=list[ChapterOut])
def admin_work_chapters(work_id: int, db: Session = Depends(get_db), _: User = Depends(require_admin)):
    return db.query(Chapter).filter(Chapter.work_id == work_id).order_by(Chapter.sort_order).all()
