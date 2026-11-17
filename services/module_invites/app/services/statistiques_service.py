"""Statistiques des invitations d'un événement."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from app.core.exceptions import NotFoundError, ServiceUnavailableError, UpstreamError
from app.core.security import AccessPolicy
from app.schemas.statistiques import StatistiquesInvitations
from app.services.capacite import places_occupees

if TYPE_CHECKING:
    from app.clients.evenement_client import EvenementClient
    from app.clients.salle_client import SalleClient
    from app.repositories.invitation_repository import InvitationRepository

logger = logging.getLogger(__name__)


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
        salles: SalleClient,
        access: AccessPolicy | None = None,
    ) -> None:
        self._evenements = evenements
        self._invitations = invitations
        self._salles = salles
        self._access = access or AccessPolicy()

    def occupation(self, evenement_id: int) -> dict[str, int]:
        """Count only: never calls Salles (which holds the capacity-change lock)."""
        evenement = self._evenements.get(evenement_id)
        if evenement is None:
            raise NotFoundError("EVENEMENT_INTROUVABLE", f"Événement {evenement_id} introuvable.")
        self._access.verifier_evenement(evenement)
        return {"evenement_id": evenement_id,
                "places_occupees": places_occupees(self._invitations.compter_par_statut(evenement_id))}

    def pour_evenement(self, evenement_id: int) -> StatistiquesInvitations:
        evenement = self._evenements.get(evenement_id)
        if evenement is None:
            raise NotFoundError("EVENEMENT_INTROUVABLE", f"Événement {evenement_id} introuvable.")
        self._access.verifier_evenement(evenement)
        compteurs = self._invitations.compter_par_statut(evenement_id)
        total = compteurs["total"]
        occupees = places_occupees(compteurs)
        # Des statistiques ne doivent pas échouer parce que le service Salles est arrêté : la capacité
        # est alors simplement inconnue (null), et c'est journalisé.
        try:
            capacite = self._salles.capacite_evenement(evenement_id).total
        except (ServiceUnavailableError, UpstreamError):
            logger.warning("Capacité de l'événement %s indisponible (service Salles).", evenement_id)
            capacite = None
        return StatistiquesInvitations(
            evenement_id=evenement_id,
            total=total,
            en_attente=compteurs["en_attente"],
            acceptees=compteurs["acceptees"],
            refusees=compteurs["refusees"],
            pourcentage_acceptation=calculer_pourcentage(compteurs["acceptees"], total),
            pourcentage_refus=calculer_pourcentage(compteurs["refusees"], total),
            places_occupees=occupees,
            capacite=capacite,
            places_restantes=None if capacite is None else max(0, capacite - occupees),
            capacite_depassee=capacite is not None and occupees > capacite,
        )
