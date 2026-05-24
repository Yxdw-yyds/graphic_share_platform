from dataclasses import dataclass


AI_APPROVED = "ai_approved"
APPROVED = "approved"
PENDING = "pending"
REJECTED = "rejected"
PUBLIC_WORK_STATUSES = (APPROVED, AI_APPROVED)
REVIEW_QUEUE_STATUSES = (PENDING, AI_APPROVED)


BLOCKED_TERMS = {
    "涉黄",
    "暴力血腥",
    "诈骗",
    "赌博",
    "毒品",
    "违禁品",
}


@dataclass(frozen=True)
class AiReviewResult:
    approved: bool
    note: str


def simple_ai_review(title: str, summary: str | None, content: str, category: str) -> AiReviewResult:
    text = f"{title}\n{summary or ''}\n{category}\n{content}".lower()
    matched = [term for term in BLOCKED_TERMS if term.lower() in text]
    if matched:
        return AiReviewResult(False, f"AI初审未通过，命中敏感词：{matched[0]}。等待管理员人工审核。")
    if len(content.strip()) < 2:
        return AiReviewResult(False, "AI初审未通过，正文内容过短。等待管理员人工审核。")
    return AiReviewResult(True, "AI初审通过，已临时发布，等待管理员终审。")
