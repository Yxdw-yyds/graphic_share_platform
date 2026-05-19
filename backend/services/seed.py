from sqlalchemy.orm import Session

from backend.core.security import hash_password
from backend.models import Chapter, User, Work
from backend.services.metrics import refresh_work_metrics


def seed_data(db: Session) -> None:
    admin = db.query(User).filter(User.account == "admin").first()
    if not admin:
        admin = User(
            account="admin",
            password_hash=hash_password("Admin@123456"),
            nickname="Admin",
            role="admin",
        )
        db.add(admin)

    if db.query(User).filter(User.account == "user").first():
        db.commit()
        return

    user = User(account="user", password_hash=hash_password("User@123456"), nickname="演示用户")
    db.add(user)
    db.flush()

    samples = [
        ("今天的天空真的好漂亮", "生活分享", "今天的天空真的好漂亮，心情也跟着变好了。", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?fit=crop&w=600&q=80"),
        ("新徒步路线记录", "运动健康", "探索了一条新徒步路线，风景超棒。", "https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?fit=crop&w=600&q=80"),
        ("第一次拉花咖啡", "生活分享", "刚学会了拉花咖啡，第一次做成图案。", "https://images.unsplash.com/photo-1541167760496-1628856ab772?fit=crop&w=600&q=80"),
    ]
    for title, category, content, cover in samples:
        work = Work(
            author_id=user.id,
            title=title,
            summary=content[:80],
            category=category,
            cover_image=cover,
            status="approved",
            view_count=12,
        )
        db.add(work)
        db.flush()
        db.add(Chapter(work_id=work.id, title="正文", content=content, image_url=cover, status="approved"))
        refresh_work_metrics(db, work)

    db.commit()
