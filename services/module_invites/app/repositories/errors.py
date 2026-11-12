"""Interprétation des erreurs d'intégrité PostgreSQL.
"""

from __future__ import annotations

import re
from enum import StrEnum
from typing import Any

UNIQUE_VIOLATION = "23505"
FOREIGN_KEY_VIOLATION = "23503"

# « Key (evenement_id)=(999) is not present in table "evenements". » (message de PostgreSQL)
_CLE_ABSENTE = re.compile(r"Key \(([^)]*)\)=.*is not present in table", re.IGNORECASE | re.DOTALL)
# « Key (id)=(1) is still referenced from table "invitations". »
_CLE_REFERENCEE = re.compile(r"is still referenced from table \"?(\w+)\"?", re.IGNORECASE)
# « Key (invite_id, evenement_id)=(1, 1) already exists. » (colonnes de l'unicité)
_CLE_EXISTANTE = re.compile(r"Key \(([^)]*)\)=.*already exists", re.IGNORECASE | re.DOTALL)


class ErreurIntegrite(StrEnum):
    ASSOCIATION_EN_DOUBLE = "ASSOCIATION_EN_DOUBLE"
    INVITE_ABSENT = "INVITE_ABSENT"
    EVENEMENT_ABSENT = "EVENEMENT_ABSENT"
    INVITE_ENCORE_REFERENCE = "INVITE_ENCORE_REFERENCE"


def _diagnostic(exc: Any) -> Any:
    return getattr(getattr(exc, "orig", None), "diag", None)


def nom_contrainte(exc: Any) -> str | None:
    """Nom de la contrainte violée d'une ``IntegrityError`` (pilote psycopg 3), ou None."""
    nom = getattr(_diagnostic(exc), "constraint_name", None)
    return nom if isinstance(nom, str) else None


def _sqlstate(exc: Any) -> str | None:
    origine = getattr(exc, "orig", None)
    for valeur in (
        getattr(origine, "sqlstate", None),  # psycopg 3
        getattr(_diagnostic(exc), "sqlstate", None),
        getattr(origine, "pgcode", None),  # psycopg 2
    ):
        if isinstance(valeur, str):
            return valeur
    return None


def classer_erreur_integrite(exc: Any) -> ErreurIntegrite | None:
    """Reconnaît les violations d'intégrité attendues sur ``invitations`` ; None si inconnue."""
    etat = _sqlstate(exc)
    diag = _diagnostic(exc)
    detail = getattr(diag, "message_detail", None)
    detail = detail if isinstance(detail, str) else ""
    contrainte = (nom_contrainte(exc) or "").lower()

    if etat == UNIQUE_VIOLATION:
        colonnes = _CLE_EXISTANTE.search(detail)
        if colonnes:
            noms = {c.strip() for c in colonnes.group(1).split(",")}
            if noms == {"invite_id", "evenement_id"}:
                return ErreurIntegrite.ASSOCIATION_EN_DOUBLE
            return None
        if "invitation" in contrainte:  # repli : nom de contrainte (uk_/uq_ …_invitations_…)
            return ErreurIntegrite.ASSOCIATION_EN_DOUBLE
        return None

    if etat == FOREIGN_KEY_VIOLATION:
        absente = _CLE_ABSENTE.search(detail)
        if absente:
            colonne = absente.group(1).strip()
            if colonne == "invite_id":
                return ErreurIntegrite.INVITE_ABSENT
            if colonne == "evenement_id":
                return ErreurIntegrite.EVENEMENT_ABSENT
            return None
        reference = _CLE_REFERENCEE.search(detail)
        if reference:
            return (
                ErreurIntegrite.INVITE_ENCORE_REFERENCE
                if reference.group(1).lower() == "invitations"
                else None
            )
        # Repli sur le nom de la contrainte (fk_invitations_invite, invitations_invite_id_fkey…).
        if "invite" in contrainte and "evenement" not in contrainte:
            return ErreurIntegrite.INVITE_ABSENT
        if "evenement" in contrainte:
            return ErreurIntegrite.EVENEMENT_ABSENT
    return None
