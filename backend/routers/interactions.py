from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from backend.database import get_db
from backend.deps import get_current_user, require_admin
from backend.models import Comment, Favorite, Report, User, Work
from backend.schemas.common import CommentOut, ReportOut, UserOut, WorkOut
from backend.schemas.requests import CommentCreateIn, ReportCreateIn
from backend.services.ai_review import PUBLIC_WORK_STATUSES, REJECTED
from backend.services.metrics import refresh_work_metrics
from backend.utils.pagination import PageParams, page_params, paginate, serialize_page

router = APIRouter(prefix="/api", tags=["interactions"])


def comment_out(comment: Comment, parent: Comment | None = None) -> dict:
    data = CommentOut.model_validate(comment).model_dump(mode="json")
    if parent and parent.user:
        data["parent_user"] = UserOut.model_validate(parent.user).model_dump(mode="json")
    return data


@router.get("/works/{work_id}/comments")
def list_comments(
    work_id: int,
    db: Session = Depends(get_db),
    pp: PageParams = Depends(page_params),
):
    base_query = (
        db.query(Comment)
        .options(joinedload(Comment.user))
        .filter(
            Comment.work_id == work_id,
            Comment.parent_id.is_(None),
            Comment.status == "visible",
        )
        .order_by(Comment.created_at.desc())
    )
    total = base_query.order_by(None).count()
    if pp.enabled:
        parents = base_query.offset((pp.page - 1) * pp.size).limit(pp.size).all()
    else:
        parents = base_query.all()

    parent_ids = [comment.id for comment in parents]
    replies_by_parent: dict[int, list[Comment]] = {comment_id: [] for comment_id in parent_ids}
    if parent_ids:
        replies = (
            db.query(Comment)
            .options(joinedload(Comment.user))
            .filter(
                Comment.work_id == work_id,
                Comment.parent_id.in_(parent_ids),
                Comment.status == "visible",
            )
            .order_by(Comment.created_at.asc())
            .all()
        )
        for reply in replies:
            if reply.parent_id in replies_by_parent:
                replies_by_parent[reply.parent_id].append(reply)

    items = [
        {
            **comment_out(comment),
            "replies": [
                comment_out(reply, comment)
                for reply in replies_by_parent.get(comment.id, [])
            ],
        }
        for comment in parents
    ]
    if not pp.enabled:
        return items
    return {
        "items": items,
        "total": total,
        "page": pp.page,
        "size": pp.size,
        "pages": (total + pp.size - 1) // pp.size if pp.size else 0,
    }


@router.get("/comments/{comment_id}", response_model=CommentOut)
def get_comment(comment_id: int, db: Session = Depends(get_db)):
    comment = (
        db.query(Comment)
        .options(joinedload(Comment.user))
        .filter(Comment.id == comment_id)
        .first()
    )
    if not comment:
        raise HTTPException(status_code=404, detail="评论不存在")
    return comment


@router.post("/works/{work_id}/comments", response_model=CommentOut)
def create_comment(work_id: int, payload: CommentCreateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    work = db.query(Work).filter(Work.id == work_id).first()
    if not work or work.status not in PUBLIC_WORK_STATUSES:
        raise HTTPException(status_code=404, detail="作品不存在")
    if payload.parent_id is not None:
        parent = (
            db.query(Comment)
            .filter(
                Comment.id == payload.parent_id,
                Comment.work_id == work_id,
                Comment.parent_id.is_(None),
                Comment.status == "visible",
            )
            .first()
        )
        if not parent:
            raise HTTPException(status_code=400, detail="回复的评论不存在")
    comment = Comment(work_id=work_id, user_id=user.id, **payload.model_dump())
    db.add(comment)
    db.flush()
    refresh_work_metrics(db, work)
    db.commit()
    db.refresh(comment)
    return comment


@router.delete("/comments/{comment_id}")
def delete_comment(comment_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    comment = db.query(Comment).filter(Comment.id == comment_id).first()
    if not comment:
        raise HTTPException(status_code=404, detail="评论不存在")
    if user.role != "admin" and comment.user_id != user.id:
        raise HTTPException(status_code=403, detail="无权删除该评论")
    comment.status = "deleted"
    work = db.query(Work).filter(Work.id == comment.work_id).first()
    if work:
        refresh_work_metrics(db, work)
    db.commit()
    return {"message": "评论已删除"}


@router.get("/favorites")
def my_favorites(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    pp: PageParams = Depends(page_params),
):
    query = (
        db.query(Work)
        .join(Favorite, Favorite.work_id == Work.id)
        .options(joinedload(Work.author))
        .filter(Favorite.user_id == user.id, Work.status.in_(PUBLIC_WORK_STATUSES))
        .order_by(Favorite.created_at.desc())
    )
    return serialize_page(paginate(query, pp), WorkOut)


@router.post("/reports", response_model=ReportOut)
def create_report(payload: ReportCreateIn, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    report = Report(reporter_id=user.id, **payload.model_dump())
    db.add(report)
    db.commit()
    db.refresh(report)
    return report


@router.get("/reports")
def list_reports(
    status: str | None = None,
    db: Session = Depends(get_db),
    _: User = Depends(require_admin),
    pp: PageParams = Depends(page_params),
):
    query = db.query(Report).options(joinedload(Report.reporter))
    if status:
        query = query.filter(Report.status == status)
    return serialize_page(paginate(query.order_by(Report.created_at.desc()), pp), ReportOut)


@router.post("/reports/{report_id}/handle")
def handle_report(report_id: int, approved: bool, db: Session = Depends(get_db), admin: User = Depends(require_admin)):
    report = db.query(Report).filter(Report.id == report_id).first()
    if not report:
        raise HTTPException(status_code=404, detail="举报不存在")
    report.status = "approved" if approved else "rejected"
    report.handler_id = admin.id
    report.handled_at = datetime.now(timezone.utc)
    if approved and report.target_type == "work":
        target = db.query(Work).filter(Work.id == report.target_id).first()
        if target:
            target.status = REJECTED
    if approved and report.target_type == "comment":
        target_comment = db.query(Comment).filter(Comment.id == report.target_id).first()
        if target_comment:
            target_comment.status = "deleted"
            work = db.query(Work).filter(Work.id == target_comment.work_id).first()
            if work:
                refresh_work_metrics(db, work)
    db.commit()
    return {"message": "举报已处理"}


@router.delete("/reports/{report_id}")
def delete_report(report_id: int, db: Session = Depends(get_db), _: User = Depends(require_admin)):
    report = db.query(Report).filter(Report.id == report_id).first()
    if not report:
        raise HTTPException(status_code=404, detail="举报不存在")
    if report.status == "pending":
        raise HTTPException(status_code=400, detail="待处理举报不能删除，请先处理")
    db.delete(report)
    db.commit()
    return {"message": "举报记录已删除"}
