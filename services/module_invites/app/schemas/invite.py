"""Schémas des invités. La validation est faite AVANT toute insertion en base."""

from __future__ import annotations

import re
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

EMAIL_LONGUEUR_MAX = 254
TELEPHONE_LONGUEUR_MAX = 20
MESSAGE_EMAIL = "Format d'adresse e-mail invalide."
MESSAGE_TELEPHONE = (
    "Format de téléphone invalide : 6 à 20 caractères parmi chiffres, espaces, +, -, ., (, ), "
    "avec 6 à 15 chiffres."
)

_PARTIE_LOCALE = re.compile(
    r"^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$"
)
_LABEL_DOMAINE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
_EXTENSION = re.compile(r"^(?:[A-Za-z]{2,}|xn--[A-Za-z0-9-]+)$")
_CARACTERES_TELEPHONE = re.compile(r"^\+?[0-9\s().-]+$")


def valider_email(valeur: str) -> str:
    """Vérifie le format d'une adresse e-mail (ASCII). Lève ValueError si invalide."""
    if len(valeur) > EMAIL_LONGUEUR_MAX or valeur.count("@") != 1:
        raise ValueError(MESSAGE_EMAIL)
    local, domaine = valeur.split("@")
    if not local or len(local) > 64 or not _PARTIE_LOCALE.match(local):
        raise ValueError(MESSAGE_EMAIL)
    labels = domaine.split(".")
    if len(labels) < 2 or not all(_LABEL_DOMAINE.match(label) for label in labels):
        raise ValueError(MESSAGE_EMAIL)
    if not _EXTENSION.match(labels[-1]):
        raise ValueError(MESSAGE_EMAIL)
    return valeur


def valider_telephone(valeur: str) -> str:
    """Validation souple d'un numéro de téléphone. Lève ValueError si invalide."""
    if not 6 <= len(valeur) <= TELEPHONE_LONGUEUR_MAX or not _CARACTERES_TELEPHONE.match(valeur):
        raise ValueError(MESSAGE_TELEPHONE)
    nombre_chiffres = sum(1 for caractere in valeur if caractere in "0123456789")
    if not 6 <= nombre_chiffres <= 15:
        raise ValueError(MESSAGE_TELEPHONE)
    return valeur


class InviteBase(BaseModel):
    """Champs communs à la création (POST) et au remplacement complet (PUT)."""

    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    nom: str = Field(min_length=1, max_length=150, description="Nom de l'invité (obligatoire).")
    email: str | None = Field(default=None, max_length=EMAIL_LONGUEUR_MAX)
    telephone: str | None = Field(default=None, max_length=TELEPHONE_LONGUEUR_MAX)

    @field_validator("email", "telephone", mode="before")
    @classmethod
    def _chaine_vide_en_none(cls, valeur: Any) -> Any:
        if isinstance(valeur, str) and not valeur.strip():
            return None
        return valeur

    @field_validator("email")
    @classmethod
    def _verifier_email(cls, valeur: str | None) -> str | None:
        return None if valeur is None else valider_email(valeur)

    @field_validator("telephone")
    @classmethod
    def _verifier_telephone(cls, valeur: str | None) -> str | None:
        return None if valeur is None else valider_telephone(valeur)

    @model_validator(mode="after")
    def _au_moins_un_contact(self) -> InviteBase:
        if self.email is None and self.telephone is None:
            raise ValueError("Au moins un moyen de contact (e-mail ou téléphone) est obligatoire.")
        return self


class InviteCreate(InviteBase):
    """Corps de ``POST /invites``."""


class InviteUpdate(InviteBase):
    """Corps de ``PUT /invites/{id}`` : remplace la totalité de la ressource."""


class InviteRead(BaseModel):
    """Lecture d'un invité (pas de revalidation des valeurs déjà stockées)."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    nom: str
    email: str | None
    telephone: str | None
