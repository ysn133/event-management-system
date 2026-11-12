"""Création du moteur et de la fabrique de sessions. Aucune connexion n'est ouverte à l'import."""

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker


def create_db_engine(database_url: str) -> Engine:
    """Moteur PostgreSQL ; la connexion n'est établie qu'à la première requête."""
    return create_engine(database_url, pool_pre_ping=True, pool_size=5, max_overflow=10)


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    """``expire_on_commit=False`` : les objets restent lisibles après le commit du service."""
    return sessionmaker(bind=engine, expire_on_commit=False)
