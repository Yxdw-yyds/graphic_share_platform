from pathlib import Path
from urllib.request import Request, urlopen

from sqlalchemy.orm import Session

from backend.core.config import get_settings
from backend.models import Chapter, Work


DEMO_IMAGE_SOURCES = {
    "demo-sky.jpg": "https://images.unsplash.com/photo-1506744038136-46273834b3fb?fit=crop&w=900&q=80&fm=jpg",
    "demo-hike.jpg": "https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?fit=crop&w=900&q=80&fm=jpg",
    "demo-coffee.jpg": "https://images.unsplash.com/photo-1541167760496-1628856ab772?fit=crop&w=900&q=80&fm=jpg",
    "demo-code.jpg": "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?fit=crop&w=900&q=80&fm=jpg",
    "demo-ai.jpg": "https://images.unsplash.com/photo-1677442136019-21780ecad995?fit=crop&w=900&q=80&fm=jpg",
    "demo-street.jpg": "https://images.unsplash.com/photo-1519501025264-65ba15a82390?fit=crop&w=900&q=80&fm=jpg",
    "demo-travel.jpg": "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?fit=crop&w=900&q=80&fm=jpg",
    "demo-food.jpg": "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?fit=crop&w=900&q=80&fm=jpg",
    "demo-fitness.jpg": "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?fit=crop&w=900&q=80&fm=jpg",
    "demo-campus.jpg": "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?fit=crop&w=900&q=80&fm=jpg",
    "demo-design.jpg": "https://images.unsplash.com/photo-1581291518857-4e27b48ff24e?fit=crop&w=900&q=80&fm=jpg",
}


def demo_image_url(filename: str) -> str:
    ensure_demo_image(filename)
    return f"/uploads/works/{filename}"


def ensure_demo_image(filename: str) -> Path:
    source = DEMO_IMAGE_SOURCES[filename]
    settings = get_settings()
    image_dir = Path(settings.upload_dir) / "works"
    image_dir.mkdir(parents=True, exist_ok=True)
    target = image_dir / filename
    if target.exists() and target.stat().st_size > 1024:
        return target

    request = Request(source, headers={"User-Agent": "Mozilla/5.0"})
    with urlopen(request, timeout=20) as response:
        target.write_bytes(response.read())
    return target


def migrate_external_demo_images(db: Session) -> int:
    mapping = {source.split("?")[0]: f"/uploads/works/{name}" for name, source in DEMO_IMAGE_SOURCES.items()}
    for name in DEMO_IMAGE_SOURCES:
        ensure_demo_image(name)

    changed = 0
    for work in db.query(Work).all():
        cover = (work.cover_image or "").split("?")[0]
        if cover in mapping:
            work.cover_image = mapping[cover]
            changed += 1

    for chapter in db.query(Chapter).all():
        image = (chapter.image_url or "").split("?")[0]
        if image in mapping:
            chapter.image_url = mapping[image]
            changed += 1

    if changed:
        db.commit()
    return changed
