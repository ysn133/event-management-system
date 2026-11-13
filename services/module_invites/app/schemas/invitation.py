"""Schémas des invitations et de leur envoi."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, computed_field

from app.core.enums import CanalEnvoi, EtatEnvoi, StatutInvitation, calculer_etat_envoi
from app.schemas.invite import InviteRead

IDENTIFIANT_MAX = 9_223_372_036_854_775_807  # plus grande valeur d'un bigint PostgreSQL


class InvitationRead(BaseModel):
    """``statut`` = réponse de l'invité ; ``etat_envoi`` = état technique (calculé)."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    invite_id: int
    evenement_id: int
    statut: StatutInvitation
    date_envoi: datetime | None

    @computed_field  # type: ignore[prop-decorator]
    @property
    def etat_envoi(self) -> EtatEnvoi:
        return calculer_etat_envoi(self.date_envoi)


class InvitationDetailRead(InvitationRead):
    """Invitation avec l'invité concerné."""

    invite: InviteRead


class InvitationCreate(BaseModel):
    """Corps de ``POST /evenements/{id}/invitations`` : déclenche l'envoi pour un invité associé."""

    model_config = ConfigDict(extra="forbid")

    invite_id: int = Field(gt=0, le=IDENTIFIANT_MAX)
    canal: CanalEnvoi = CanalEnvoi.EMAIL


class EnvoiResultat(BaseModel):
    """Résultat renvoyé par le service d'envoi. ``simulation=true`` : aucun message réel envoyé."""

    confirme: bool
    canal: CanalEnvoi
    fournisseur: str
    simulation: bool


class InvitationEnvoiRead(BaseModel):
    invitation: InvitationRead
    envoi: EnvoiResultat


class StatutUpdate(BaseModel):
    """Corps de ``PATCH /invitations/{id}/statut`` : seules les 3 valeurs autorisées sont acceptées."""

    model_config = ConfigDict(extra="forbid")

    statut: StatutInvitation
