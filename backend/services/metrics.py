from sqlalchemy import func
from sqlalchemy.orm import Session

from backend.models import Comment, Favorite, Like, User, Work


def calculate_heat(work: Work) -> float:
    return round(
        work.view_count * 0.2
        + work.like_count * 2
        + work.favorite_count * 3
        + work.comment_count * 1.5,
        2,
    )


def level_for_score(score: float) -> str:
    if score >= 5000:
        return "Lv5"
    if score >= 1500:
        return "Lv4"
    if score >= 500:
        return "Lv3"
    if score >= 100:
        return "Lv2"
    return "Lv1"


def refresh_work_metrics(db: Session, work: Work) -> Work:
    work.like_count = db.query(Like).filter(Like.work_id == work.id).count()
    work.favorite_count = db.query(Favorite).filter(Favorite.work_id == work.id).count()
    work.comment_count = db.query(Comment).filter(Comment.work_id == work.id, Comment.status == "visible").count()
    work.heat = calculate_heat(work)
    db.flush()
    refresh_user_level(db, work.author_id)
    return work


def refresh_user_level(db: Session, user_id: int) -> None:
    score = db.query(func.coalesce(func.sum(Work.heat), 0)).filter(Work.author_id == user_id).scalar() or 0
    user = db.query(User).filter(User.id == user_id).first()
    if user:
        user.heat_score = float(score)
        user.creator_level = level_for_score(float(score))
        db.flush()
