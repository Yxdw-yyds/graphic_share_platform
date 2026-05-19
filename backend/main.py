from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from backend import middleware
from backend.core.config import get_settings
from backend.database import Base, SessionLocal, engine
from backend.routers import admin, auth, interactions, users, works
from backend.services.seed import seed_data


settings = get_settings()
app = FastAPI(title=settings.app_name, debug=settings.debug)

middleware.install(app)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(works.router)
app.include_router(interactions.router)
app.include_router(admin.router)


@app.on_event("startup")
def startup() -> None:
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        if settings.create_demo_data:
            seed_data(db)
    finally:
        db.close()


@app.get("/api/health")
def health():
    return {"status": "ok"}


root = Path(__file__).resolve().parent.parent
uploads = root / settings.upload_dir
uploads.mkdir(exist_ok=True)
app.mount("/uploads", StaticFiles(directory=str(uploads)), name="uploads")
app.mount("/", StaticFiles(directory=str(root), html=True), name="frontend")
