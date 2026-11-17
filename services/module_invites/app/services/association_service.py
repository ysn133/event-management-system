"""Association invité ↔ événement (une ligne ``invitations`` EN_ATTENTE, non envoyée)."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from app.core.enums import StatutInvitation
from app.core.exceptions import ConflictError, NotFoundError
from app.core.security import AccessPolicy
from app.services.base import BaseService, SessionLike
from app.services.capacite import places_occupees, verifier_capacite

if TYPE_CHECKING:
    from app.clients.evenement_client import EvenementClient
    from app.clients.salle_client import SalleClient
    from app.repositories.invitation_repository import InvitationRepository
    from app.repositories.invite_repository import InviteRepository

logger = logging.getLogger(__name__)


class AssociationService(BaseService):
    def __init__(
        self,
        session: SessionLike,
        invites: InviteRepository,
        evenements: EvenementClient,
        invitations: InvitationRepository,
        salles: SalleClient,
        access: AccessPolicy | None = None,
    ) -> None:
        super().__init__(session)
        self._invites = invites
        self._evenements = evenements
        self._invitations = invitations
        self._salles = salles
        self._access = access or AccessPolicy()

    def _invite_ou_404(self, invite_id: int) -> Any:
        invite = self._invites.get(invite_id)
        if invite is None:
            raise NotFoundError("INVITE_INTROUVABLE", f"Invité {invite_id} introuvable.")
        return invite

    def _evenement_ou_404(self, evenement_id: int) -> Any:
        evenement = self._evenements.get(evenement_id)
        if evenement is None:
            raise NotFoundError("EVENEMENT_INTROUVABLE", f"Événement {evenement_id} introuvable.")
        self._access.verifier_evenement(evenement)
        return evenement

    def associer(self, evenement_id: int, invite_id: int) -> Any:
        # Event validation is outside the lock. Capacity must be fetched AFTER
        # locking: Salles uses the same event lock when it removes seats.
        self._evenement_ou_404(evenement_id)
        self._invite_ou_404(invite_id)
        if self._invitations.get_by_invite_and_event(invite_id, evenement_id) is not None:
            raise ConflictError(
                "ASSOCIATION_EXISTANTE",
                f"L'invité {invite_id} est déjà associé à l'événement {evenement_id}.",
            )
        # Le contrôle ci-dessus est un confort ; la contrainte UNIQUE (voir le dépôt) protège
        # des requêtes concurrentes.
        with self.transaction():
            # Le verrou rend « compter les places puis insérer » atomique pour cet événement.
            self._invitations.verrouiller_evenement(evenement_id)
            capacite = self._salles.capacite_evenement(evenement_id)
            verifier_capacite(capacite, places_occupees(self._invitations.compter_par_statut(evenement_id)))
            return self._invitations.add(invite_id, evenement_id)

    def invites_de_evenement(
        self, evenement_id: int, *, statut: StatutInvitation | None, limit: int, offset: int
    ) -> tuple[list[tuple[Any, Any]], int]:
        self._evenement_ou_404(evenement_id)
        return self._invitations.list_invites_of_event(
            evenement_id, statut=statut, limit=limit, offset=offset
        )

    def evenements_de_invite(
        self, invite_id: int, *, statut: StatutInvitation | None, limit: int, offset: int
    ) -> tuple[list[tuple[Any, Any]], int]:
        self._invite_ou_404(invite_id)
        invitations, total = self._invitations.list_for_invite(
            invite_id, statut=statut, limit=limit, offset=offset
        )
        # On libère la connexion avant d'appeler le service Événements (un appel par événement distinct).
        self._session.commit()
        cache: dict[int, Any] = {}
        lignes: list[tuple[Any, Any]] = []
        for invitation in invitations:
            if invitation.evenement_id not in cache:
                cache[invitation.evenement_id] = self._evenements.get(invitation.evenement_id)
            evenement = cache[invitation.evenement_id]
            if evenement is None:
                logger.warning(
                    "Événement %s référencé par l'invitation %s absent du service Événements : ignoré.",
                    invitation.evenement_id,
                    invitation.id,
                )
                continue
            lignes.append((evenement, invitation))
        return lignes, total

    def dissocier(self, evenement_id: int, invite_id: int) -> None:
        """Supprime l'association, sauf si l'invitation a déjà été envoyée ou a reçu une réponse."""
        self._evenement_ou_404(evenement_id)
        invitation = self._invitations.get_by_invite_and_event(invite_id, evenement_id)
        if invitation is None:
            raise NotFoundError(
                "ASSOCIATION_INTROUVABLE",
                f"L'invité {invite_id} n'est pas associé à l'événement {evenement_id}.",
            )
        if invitation.date_envoi is not None or invitation.statut != StatutInvitation.EN_ATTENTE.value:
            raise ConflictError(
                "ASSOCIATION_NON_SUPPRIMABLE",
                "Cette invitation a déjà été envoyée ou a reçu une réponse : "
                "l'association est conservée pour garder l'historique.",
            )
        # Le contrôle ci-dessus donne un message clair ; le DELETE conditionnel du dépôt garantit
        # qu'un envoi ou une réponse arrivé entre-temps n'est jamais effacé.
        with self.transaction():
            if not self._invitations.delete_if_pristine(invitation):
                raise ConflictError(
                    "ASSOCIATION_NON_SUPPRIMABLE",
                    "Cette invitation vient d'être envoyée ou de recevoir une réponse : "
                    "l'association est conservée pour garder l'historique.",
                )
