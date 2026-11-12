"""Énumérations métier, volontairement sans dépendance à SQLAlchemy ni FastAPI."""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum


class StatutInvitation(StrEnum):
    """Réponse de l'invité. Ce sont les trois seules valeurs autorisées."""

    EN_ATTENTE = "EN_ATTENTE"
    ACCEPTEE = "ACCEPTEE"
    REFUSEE = "REFUSEE"


class EtatEnvoi(StrEnum):
    """État technique de l'envoi. Il est CALCULÉ à partir de ``date_envoi``, jamais stocké."""

    NON_ENVOYEE = "NON_ENVOYEE"
    ENVOYEE = "ENVOYEE"


class CanalEnvoi(StrEnum):
    """Canal d'envoi d'une invitation."""

    EMAIL = "EMAIL"
    SMS = "SMS"


def calculer_etat_envoi(date_envoi: datetime | None) -> EtatEnvoi:
    """Une invitation est « envoyée » si et seulement si ``date_envoi`` est renseignée."""
    return EtatEnvoi.NON_ENVOYEE if date_envoi is None else EtatEnvoi.ENVOYEE
