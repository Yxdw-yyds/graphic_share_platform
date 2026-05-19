from datetime import datetime, timedelta, timezone
from random import randint

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from backend.core.security import create_access_token, hash_password, verify_password
from backend.database import get_db
from backend.deps import get_current_user
from backend.models import User, VerificationCode
from backend.schemas.common import Token, UserOut
from backend.schemas.requests import LoginCodeIn, LoginPasswordIn, RegisterIn, ResetPasswordIn, SendCodeIn

router = APIRouter(prefix="/api/auth", tags=["auth"])


def token_for(user: User) -> Token:
    return Token(
        access_token=create_access_token(str(user.id), {"role": user.role}),
        user=UserOut.model_validate(user).model_dump(mode="json"),
    )


def verify_code(db: Session, account: str, purpose: str, code: str) -> None:
    record = (
        db.query(VerificationCode)
        .filter(
            VerificationCode.account == account,
            VerificationCode.purpose == purpose,
            VerificationCode.code == code,
            VerificationCode.used.is_(False),
        )
        .order_by(VerificationCode.created_at.desc())
        .first()
    )
    if not record:
        raise HTTPException(status_code=400, detail="验证码错误或已过期")
    now = datetime.now(timezone.utc)
    expires_at = record.expires_at
    if expires_at and expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < now:
        raise HTTPException(status_code=400, detail="验证码错误或已过期")
    record.used = True
    db.flush()


@router.post("/send-code")
def send_code(payload: SendCodeIn, db: Session = Depends(get_db)):
    code = str(randint(100000, 999999))
    record = VerificationCode(
        account=payload.account,
        purpose=payload.purpose,
        code=code,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=5),
    )
    db.add(record)
    db.commit()
    return {"message": "验证码已生成", "code": code}


@router.post("/register", response_model=Token)
def register(payload: RegisterIn, db: Session = Depends(get_db)):
    if db.query(User).filter(User.account == payload.account).first():
        raise HTTPException(status_code=400, detail="该账号已被注册")
    verify_code(db, payload.account, "register", payload.code)
    user = User(
        account=payload.account,
        password_hash=hash_password(payload.password),
        nickname=payload.nickname or payload.account,
        contact=payload.account,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return token_for(user)


@router.post("/login/password", response_model=Token)
def login_password(payload: LoginPasswordIn, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.account == payload.account).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=400, detail="账号或密码错误")
    if user.status != "active":
        raise HTTPException(status_code=403, detail="账号已被禁用")
    return token_for(user)


@router.post("/login/code", response_model=Token)
def login_code(payload: LoginCodeIn, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.account == payload.account).first()
    if not user:
        raise HTTPException(status_code=404, detail="该账号不存在，请先注册")
    verify_code(db, payload.account, "login", payload.code)
    return token_for(user)


@router.post("/reset-password")
def reset_password(payload: ResetPasswordIn, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.account == payload.account).first()
    if not user:
        raise HTTPException(status_code=404, detail="该账号不存在")
    verify_code(db, payload.account, "reset", payload.code)
    user.password_hash = hash_password(payload.new_password)
    db.commit()
    return {"message": "密码重置成功"}


@router.get("/me", response_model=UserOut)
def me(user: User = Depends(get_current_user)):
    return user
