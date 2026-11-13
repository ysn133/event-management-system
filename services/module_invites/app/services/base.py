"""Socle commun des services : gestion de la transaction."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from typing import Protocol


class SessionLike(Protocol):
    """Ce dont un service a besoin d'une session SQLAlchemy (facilite les tests unitaires)."""

    def commit(self) -> None:
        """Valide la transaction en cours."""

    def rollback(self) -> None:
        """Annule la transaction en cours."""


class BaseService:
    def __init__(self, session: SessionLike) -> None:
        self._session = session

    @contextmanager
    def transaction(self) -> Iterator[None]:
        """Commit si le bloc réussit ; rollback puis relance l'exception dans tous les autres cas."""
        try:
            yield
            self._session.commit()
        except BaseException:
            self._session.rollback()
            raise
