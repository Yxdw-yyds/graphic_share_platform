from math import ceil
from typing import Generic, TypeVar

from fastapi import Query
from pydantic import BaseModel
from sqlalchemy.orm import Query as SAQuery

T = TypeVar("T")


class PageParams(BaseModel):
    page: int | None = None
    size: int = 20

    @property
    def enabled(self) -> bool:
        return self.page is not None and self.page >= 1


def page_params(
    page: int | None = Query(default=None, ge=1, description="页码，从 1 开始；不传则返回全部"),
    size: int = Query(default=20, ge=1, le=100, description="每页数量，最大 100"),
) -> PageParams:
    return PageParams(page=page, size=size)


class Page(BaseModel, Generic[T]):
    items: list[T]
    total: int
    page: int
    size: int
    pages: int


def paginate(query: SAQuery, params: PageParams):
    """如果开启了分页则返回 dict（FastAPI 会按 response_model 序列化），否则返回 list。"""
    if not params.enabled:
        return query.all()
    total = query.order_by(None).count()
    items = query.offset((params.page - 1) * params.size).limit(params.size).all()
    return {
        "items": items,
        "total": total,
        "page": params.page,
        "size": params.size,
        "pages": ceil(total / params.size) if params.size else 0,
    }


def serialize_page(result, schema):
    if isinstance(result, dict):
        result["items"] = [schema.model_validate(item).model_dump(mode="json") for item in result["items"]]
        return result
    return [schema.model_validate(item).model_dump(mode="json") for item in result]
