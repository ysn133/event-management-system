"""Statistiques des invitations d'un événement."""

from __future__ import annotations

from typing import TYPE_CHECKING

from app.core.exceptions import NotFoundError
from app.core.security import AccessPolicy
from app.schemas.statistiques import StatistiquesInvitations

if TYPE_CHECKING:
    from app.clients.evenement_client import EvenementClient
    from app.repositories.invitation_repository import InvitationRepository


def calculer_pourcentage(partie: int, total: int) -> float:
    """Pourcentage arrondi à 2 décimales ; 0.0 si le total est nul (pas de division par zéro)."""
    if total <= 0:
        return 0.0
    return round(partie * 100 / total, 2)


class StatistiquesService:
    def __init__(
        self,
        evenements: EvenementClient,
        invitations: InvitationRepository,
        access: AccessPolicy | None = None,
    ) -> None:
        self._evenements = evenements
        self._invitations = invitations
        self._access = access or AccessPolicy()

    def pour_evenement(self, evenement_id: int) -> StatistiquesInvitations:
        evenement = self._evenements.get(evenement_id)
        if evenement is None:
            raise NotFoundError("EVENEMENT_INTROUVABLE", f"Événement {evenement_id} introuvable.")
        self._access.verifier_evenement(evenement)
        compteurs = self._invitations.compter_par_statut(evenement_id)
        total = compteurs["total"]
        return StatistiquesInvitations(
            evenement_id=evenement_id,
            total=total,
            en_attente=compteurs["en_attente"],
            acceptees=compteurs["acceptees"],
            refusees=compteurs["refusees"],
            pourcentage_acceptation=calculer_pourcentage(compteurs["acceptees"], total),
            pourcentage_refus=calculer_pourcentage(compteurs["refusees"], total),
        )
