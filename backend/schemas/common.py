from datetime import datetime

from pydantic import BaseModel, ConfigDict


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class Message(BaseModel):
    message: str


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: dict


class UserOut(ORMModel):
    id: int
    account: str
    nickname: str
    avatar_url: str | None = None
    bio: str | None = None
    contact: str | None = None
    role: str
    status: str
    creator_level: str
    heat_score: float
    created_at: datetime


class WorkOut(ORMModel):
    id: int
    author_id: int
    title: str
    summary: str | None = None
    category: str
    cover_image: str | None = None
    status: str
    review_note: str | None = None
    view_count: int
    like_count: int
    favorite_count: int
    comment_count: int
    heat: float
    created_at: datetime
    updated_at: datetime
    author: UserOut | None = None


class ChapterOut(ORMModel):
    id: int
    work_id: int
    title: str
    content: str
    image_url: str | None = None
    sort_order: int
    status: str
    created_at: datetime


class CommentOut(ORMModel):
    id: int
    work_id: int
    chapter_id: int | None = None
    user_id: int
    parent_id: int | None = None
    content: str
    status: str
    created_at: datetime
    user: UserOut | None = None
    replies: list["CommentOut"] = []


class ReportOut(ORMModel):
    id: int
    reporter_id: int
    target_type: str
    target_id: int
    reason: str
    description: str | None = None
    status: str
    created_at: datetime
    reporter: UserOut | None = None


class FollowOut(ORMModel):
    id: int
    follower_id: int
    followed_id: int
    created_at: datetime
    followed: UserOut | None = None
