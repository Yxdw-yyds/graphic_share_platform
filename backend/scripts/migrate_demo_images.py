from backend.database import SessionLocal
from backend.services.demo_images import migrate_external_demo_images


def main() -> None:
    db = SessionLocal()
    try:
        changed = migrate_external_demo_images(db)
        print(f"updated {changed} image references")
    finally:
        db.close()


if __name__ == "__main__":
    main()
