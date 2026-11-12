"""Base déclarative SQLAlchemy partagée par les modèles du module."""

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Classe de base de tous les modèles."""
