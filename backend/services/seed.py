from sqlalchemy.orm import Session

from backend.core.security import hash_password
from backend.models import Chapter, User, Work
from backend.services.demo_images import demo_image_url
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
        ("今天的天空真的好漂亮", "生活分享", "今天的天空真的好漂亮，心情也跟着变好了。", demo_image_url("demo-sky.jpg")),
        ("新徒步路线记录", "运动健康", "探索了一条新徒步路线，风景超棒。", demo_image_url("demo-hike.jpg")),
        ("第一次拉花咖啡", "生活分享", "刚学会了拉花咖啡，第一次做成图案。", demo_image_url("demo-coffee.jpg")),
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
