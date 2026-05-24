from typing import Literal

from pydantic import BaseModel, Field, field_validator

# 账号：手机号（11 位数字）或邮箱
ACCOUNT_PATTERN = r"^(?:1\d{10}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})$"

REPORT_REASONS = {"垃圾广告", "违法违规", "涉黄涉暴", "人身攻击", "其他"}
REPORT_TARGETS = {"work", "comment"}


class RegisterIn(BaseModel):
    account: str = Field(min_length=3, max_length=120, pattern=ACCOUNT_PATTERN)
    password: str = Field(min_length=8, max_length=128)
    code: str = Field(min_length=4, max_length=12)
    nickname: str | None = Field(default=None, min_length=1, max_length=80)


class LoginPasswordIn(BaseModel):
    account: str = Field(min_length=3, max_length=120)
    password: str = Field(min_length=1, max_length=128)


class LoginCodeIn(BaseModel):
    account: str = Field(min_length=3, max_length=120)
    code: str = Field(min_length=4, max_length=12)


class SendCodeIn(BaseModel):
    account: str = Field(min_length=3, max_length=120, pattern=ACCOUNT_PATTERN)
    purpose: Literal["login", "register", "reset"] = "login"


class ResetPasswordIn(BaseModel):
    account: str = Field(min_length=3, max_length=120)
    code: str = Field(min_length=4, max_length=12)
    new_password: str = Field(min_length=8, max_length=128)


class UserUpdateIn(BaseModel):
    nickname: str | None = Field(default=None, min_length=1, max_length=80)
    bio: str | None = Field(default=None, max_length=500)
    contact: str | None = Field(default=None, max_length=120)
    avatar_url: str | None = Field(default=None, max_length=500)


class AdminUserUpdateIn(UserUpdateIn):
    role: Literal["user", "admin"] | None = None
    status: Literal["active", "disabled", "deleted"] | None = None


class AdminPasswordResetIn(BaseModel):
    new_password: str = Field(min_length=8, max_length=128)


class ChangePasswordIn(BaseModel):
    old_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=8, max_length=128)


class WorkCreateIn(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    summary: str | None = Field(default=None, max_length=500)
    category: str = Field(default="生活分享", min_length=1, max_length=40)
    cover_image: str | None = None
    first_chapter_title: str = Field(default="正文", min_length=1, max_length=100)
    first_chapter_content: str = Field(min_length=1, max_length=20000)


class WorkUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=100)
    summary: str | None = Field(default=None, max_length=500)
    category: str | None = Field(default=None, min_length=1, max_length=40)
    cover_image: str | None = None


class ChapterCreateIn(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    content: str = Field(min_length=1, max_length=20000)
    image_url: str | None = None
    sort_order: int = Field(default=1, ge=1, le=999)


class ChapterUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=100)
    content: str | None = Field(default=None, max_length=20000)
    image_url: str | None = None
    sort_order: int | None = Field(default=None, ge=1, le=999)


class CollectionCreateIn(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    description: str | None = Field(default=None, max_length=500)
    cover_image: str | None = None


class CollectionUpdateIn(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=120)
    description: str | None = Field(default=None, max_length=500)
    cover_image: str | None = None


class CollectionItemCreateIn(BaseModel):
    work_id: int = Field(ge=1)
    sort_order: int | None = Field(default=None, ge=1, le=999)


class CollectionItemUpdateIn(BaseModel):
    sort_order: int = Field(ge=1, le=999)


class CommentCreateIn(BaseModel):
    content: str = Field(min_length=1, max_length=500)
    parent_id: int | None = Field(default=None, ge=1)
    chapter_id: int | None = Field(default=None, ge=1)


class ReportCreateIn(BaseModel):
    target_type: str
    target_id: int = Field(ge=1)
    reason: str
    description: str | None = Field(default=None, max_length=500)

    @field_validator("target_type")
    @classmethod
    def _check_target(cls, v: str) -> str:
        if v not in REPORT_TARGETS:
            raise ValueError(f"target_type 必须是 {sorted(REPORT_TARGETS)} 之一")
        return v

    @field_validator("reason")
    @classmethod
    def _check_reason(cls, v: str) -> str:
        if v not in REPORT_REASONS:
            raise ValueError(f"reason 必须是 {sorted(REPORT_REASONS)} 之一")
        return v


class ReviewIn(BaseModel):
    approved: bool
    note: str | None = Field(default=None, max_length=500)
