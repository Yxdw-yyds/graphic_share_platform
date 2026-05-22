import smtplib
from email.message import EmailMessage

from backend.core.config import get_settings


def send_verification_email(to_account: str, code: str, purpose: str) -> bool:
    if "@" not in to_account:
        return False

    settings = get_settings()
    if not settings.smtp_password:
        return False

    purpose_text = {
        "register": "注册",
        "login": "登录",
        "reset": "重置密码",
    }.get(purpose, "验证")
    message = EmailMessage()
    message["Subject"] = f"αcFun {purpose_text}验证码"
    message["From"] = settings.smtp_sender
    message["To"] = to_account
    message.set_content(
        f"您的 αcFun {purpose_text}验证码是：{code}\n\n"
        "验证码 5 分钟内有效，请勿泄露给他人。"
    )

    if settings.smtp_use_ssl:
        with smtplib.SMTP_SSL(settings.smtp_host, settings.smtp_port, timeout=15) as smtp:
            smtp.login(settings.smtp_username, settings.smtp_password)
            smtp.send_message(message)
    else:
        with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as smtp:
            smtp.starttls()
            smtp.login(settings.smtp_username, settings.smtp_password)
            smtp.send_message(message)
    return True
