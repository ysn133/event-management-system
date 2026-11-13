"""Schémas des associations invité ↔ événement."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, computed_field

from app.core.enums import EtatEnvoi, StatutInvitation, calculer_etat_envoi
from app.schemas.invite import InviteRead


class SuiviInvitation(BaseModel):
    """Suivi de l'invitation liée à l'association (réponse + état d'envoi calculé)."""

    model_config = ConfigDict(from_attributes=True)

    invitation_id: int
    statut_invitation: StatutInvitation
    date_envoi: datetime | None

    @computed_field  # type: ignore[prop-decorator]
    @property
    def etat_envoi(self) -> EtatEnvoi:
        return calculer_etat_envoi(self.date_envoi)


class InviteAssocieRead(InviteRead, SuiviInvitation):
    """Un invité associé à un événement, avec le suivi de son invitation."""

    @classmethod
    def depuis(cls, invite: Any, invitation: Any) -> InviteAssocieRead:
        return cls(
            id=invite.id,
            nom=invite.nom,
            email=invite.email,
            telephone=invite.telephone,
            invitation_id=invitation.id,
            statut_invitation=invitation.statut,
            date_envoi=invitation.date_envoi,
        )


class EvenementResume(BaseModel):
    """Résumé d'un événement (``statut`` est celui de l'événement, pas de l'invitation)."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    titre: str
    date_debut: datetime
    date_fin: datetime
    lieu: str | None
    statut: str


class EvenementAssocieRead(EvenementResume, SuiviInvitation):
    """Un événement auquel un invité est associé, avec le suivi de son invitation."""

    @classmethod
    def depuis(cls, evenement: Any, invitation: Any) -> EvenementAssocieRead:
        return cls(
            id=evenement.id,
            titre=evenement.titre,
            date_debut=evenement.date_debut,
            date_fin=evenement.date_fin,
            lieu=evenement.lieu,
            statut=evenement.statut,
            invitation_id=invitation.id,
            statut_invitation=invitation.statut,
            date_envoi=invitation.date_envoi,
        )
