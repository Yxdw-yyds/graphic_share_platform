from datetime import date, datetime, time, timezone

from fastapi import APIRouter, Depends
from fastapi import HTTPException
from sqlalchemy.orm import Session, joinedload

from backend.database import get_db
from backend.deps import require_admin
from backend.models import Chapter, Report, User, Work
from backend.schemas.common import ChapterOut, WorkOut

router = APIRouter(prefix="/api/admin", tags=["admin"])


@router.get("/stats")
def stats(db: Session = Depends(get_db), _: User = Depends(require_admin)):
    start = datetime.combine(date.today(), time.min, tzinfo=timezone.utc)
    return {
        "new_users_today": db.query(User).filter(User.created_at >= start).count(),
        "works_today": db.query(Work).filter(Work.created_at >= start).count(),
        "pending_works": db.query(Work).filter(Work.status == "pending").count(),
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


@router.get("/works/{work_id}/chapters", response_model=list[ChapterOut])
def admin_work_chapters(work_id: int, db: Session = Depends(get_db), _: User = Depends(require_admin)):
    return db.query(Chapter).filter(Chapter.work_id == work_id).order_by(Chapter.sort_order).all()
