"""Schémas communs : pagination et format d'erreur."""

from __future__ import annotations

from typing import Any, Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class Page(BaseModel, Generic[T]):
    """Réponse paginée."""

    items: list[T]
    total: int = Field(ge=0, description="Nombre total d'éléments avant pagination.")
    limit: int
    offset: int


class ErreurDetail(BaseModel):
    code: str = Field(description="Code d'erreur stable, utilisable côté client.")
    message: str
    details: list[dict[str, Any]] | None = None


class ErrorResponse(BaseModel):
    """Format unique de toutes les erreurs : ``{"error": {"code", "message", "details"?}}``."""

    error: ErreurDetail
