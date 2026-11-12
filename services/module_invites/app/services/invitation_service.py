"""Invitations : envoi, consultation et suivi des réponses.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from datetime import UTC, datetime
from typing import TYPE_CHECKING, Any

from app.core.enums import CanalEnvoi, StatutInvitation
from app.core.exceptions import (
    AppError,
    BusinessValidationError,
    ConflictError,
    NotFoundError,
    NotificationFailedError,
)
from app.core.security import AccessPolicy
from app.schemas.invitation import InvitationCreate
from app.services.base import BaseService, SessionLike
from app.services.notification_service import (
    Destinataire,
    MessageInvitation,
    NotificationSender,
    ResultatEnvoi,
)

if TYPE_CHECKING:
    from app.clients.evenement_client import EvenementClient
    from app.repositories.invitation_repository import InvitationRepository
    from app.repositories.invite_repository import InviteRepository

logger = logging.getLogger(__name__)


def _maintenant_utc() -> datetime:
    return datetime.now(UTC)


class InvitationService(BaseService):
    def __init__(
        self,
        session: SessionLike,
        invites: InviteRepository,
        evenements: EvenementClient,
        invitations: InvitationRepository,
        sender: NotificationSender,
        access: AccessPolicy | None = None,
        clock: Callable[[], datetime] = _maintenant_utc,
    ) -> None:
        super().__init__(session)
        self._invites = invites
        self._evenements = evenements
        self._invitations = invitations
        self._sender = sender
        self._access = access or AccessPolicy()
        self._clock = clock

    # ------------------------------------------------------------------ helpers

    def _evenement_ou_404(self, evenement_id: int) -> Any:
        evenement = self._evenements.get(evenement_id)
        if evenement is None:
            raise NotFoundError("EVENEMENT_INTROUVABLE", f"Événement {evenement_id} introuvable.")
        self._access.verifier_evenement(evenement)
        return evenement

    def _invite_ou_404(self, invite_id: int) -> Any:
        invite = self._invites.get(invite_id)
        if invite is None:
            raise NotFoundError("INVITE_INTROUVABLE", f"Invité {invite_id} introuvable.")
        return invite

    def _verifier_acces_invitation(self, invitation: Any) -> None:
        if self._access.enabled:
            self._evenement_ou_404(invitation.evenement_id)

    @staticmethod
    def _destinataire(invite: Any, canal: CanalEnvoi) -> Destinataire:
        if canal == CanalEnvoi.EMAIL and not invite.email:
            raise BusinessValidationError(
                "CONTACT_MANQUANT",
                "L'invité n'a pas d'adresse e-mail : impossible d'envoyer l'invitation par e-mail.",
            )
        if canal == CanalEnvoi.SMS and not invite.telephone:
            raise BusinessValidationError(
                "CONTACT_MANQUANT",
                "L'invité n'a pas de numéro de téléphone : impossible d'envoyer l'invitation par SMS.",
            )
        return Destinataire(nom=invite.nom, email=invite.email, telephone=invite.telephone)

    def _envoyer_via_fournisseur(
        self, canal: CanalEnvoi, destinataire: Destinataire, message: MessageInvitation
    ) -> ResultatEnvoi:
        try:
            resultat = self._sender.envoyer(canal, destinataire, message)
        except AppError:
            raise  # ex. fournisseur non configuré (503)
        except Exception as exc:  # noqa: BLE001 - toute panne du fournisseur devient un 502
            logger.exception("Échec inattendu du service d'envoi (invitation %s).", message.invitation_id)
            raise NotificationFailedError(
                "ENVOI_ECHEC", "Le fournisseur d'envoi a échoué : l'invitation n'a pas été envoyée."
            ) from exc
        if not resultat.confirme:
            logger.warning("Envoi non confirmé pour l'invitation %s.", message.invitation_id)
            raise NotificationFailedError(
                "ENVOI_NON_CONFIRME",
                "Le fournisseur n'a pas confirmé l'envoi : l'invitation n'a pas été marquée comme envoyée.",
            )
        return resultat

    # ------------------------------------------------------------------ cas d'usage

    def envoyer(self, evenement_id: int, data: InvitationCreate) -> tuple[Any, ResultatEnvoi]:
        """Envoie l'invitation d'un invité DÉJÀ associé ; enregistre ``date_envoi`` si confirmé."""
        evenement = self._evenement_ou_404(evenement_id)
        invite = self._invite_ou_404(data.invite_id)
        invitation = self._invitations.get_by_invite_and_event(invite.id, evenement.id)
        if invitation is None:
            raise ConflictError(
                "ASSOCIATION_INTROUVABLE",
                "Cet invité n'est pas associé à cet événement : associez-le d'abord "
                f"(POST /api/v1/evenements/{evenement_id}/invites/{data.invite_id}).",
            )
        if invitation.date_envoi is not None:
            raise ConflictError(
                "INVITATION_DEJA_ENVOYEE", "Cette invitation a déjà été envoyée."
            )
        destinataire = self._destinataire(invite, data.canal)
        message = MessageInvitation(
            invitation_id=invitation.id,
            evenement_titre=evenement.titre,
            evenement_lieu=evenement.lieu,
            evenement_date_debut=evenement.date_debut,
        )

        # On termine la transaction de lecture AVANT l'appel externe : aucune connexion ni verrou
        # n'est conservé pendant que l'on attend le fournisseur.
        self._session.commit()
        resultat = self._envoyer_via_fournisseur(data.canal, destinataire, message)

        with self.transaction():
            if not self._invitations.marquer_envoyee(invitation, self._clock()):
                raise ConflictError(
                    "INVITATION_DEJA_ENVOYEE",
                    "Cette invitation a été marquée comme envoyée par une autre requête.",
                )
        return invitation, resultat

    def obtenir(self, invitation_id: int) -> Any:
        invitation = self._invitations.get_with_invite(invitation_id)
        if invitation is None:
            raise NotFoundError("INVITATION_INTROUVABLE", f"Invitation {invitation_id} introuvable.")
        self._verifier_acces_invitation(invitation)
        return invitation

    def lister_evenement(
        self,
        evenement_id: int,
        *,
        statut: StatutInvitation | None,
        envoyee: bool | None,
        limit: int,
        offset: int,
    ) -> tuple[list[Any], int]:
        self._evenement_ou_404(evenement_id)
        return self._invitations.list_for_event(
            evenement_id, statut=statut, envoyee=envoyee, limit=limit, offset=offset
        )

    def lister_invite(
        self, invite_id: int, *, statut: StatutInvitation | None, limit: int, offset: int
    ) -> tuple[list[Any], int]:
        self._invite_ou_404(invite_id)
        return self._invitations.list_for_invite(
            invite_id, statut=statut, limit=limit, offset=offset
        )

    def changer_statut(self, invitation_id: int, statut: StatutInvitation | str) -> Any:
        """Enregistre la réponse de l'invité. Toute transition entre les 3 statuts est permise."""
        try:
            nouveau_statut = StatutInvitation(statut)
        except ValueError as exc:
            raise BusinessValidationError(
                "STATUT_INVALIDE",
                "Statut invalide : valeurs autorisées EN_ATTENTE, ACCEPTEE, REFUSEE.",
            ) from exc
        invitation = self._invitations.get(invitation_id)
        if invitation is None:
            raise NotFoundError("INVITATION_INTROUVABLE", f"Invitation {invitation_id} introuvable.")
        self._verifier_acces_invitation(invitation)
        with self.transaction():
            return self._invitations.update_statut(invitation, nouveau_statut)
