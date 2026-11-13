"""Modèles SQLAlchemy. Importer ce paquet enregistre toutes les tables dans ``Base.metadata``."""

from app.models.invitation import Invitation
from app.models.invite import Invite

__all__ = ["Invitation", "Invite"]
