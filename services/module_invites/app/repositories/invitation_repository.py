"""Accès aux invitations (= association invité ↔ événement + suivi)."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Select, delete, func, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload

from app.core.enums import StatutInvitation
from app.core.exceptions import ConflictError, NotFoundError
from app.models.invitation import Invitation
from app.models.invite import Invite
from app.repositories.errors import ErreurIntegrite, classer_erreur_integrite


class InvitationRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    # ------------------------------------------------------------------ écriture

    def add(self, invite_id: int, evenement_id: int) -> Invitation:
        """Crée l'association (EN_ATTENTE, non envoyée).

        La contrainte d'unicité PostgreSQL est la vraie protection contre les doublons
        concurrents : sa violation est convertie en 409.
        """
        invitation = Invitation(
            invite_id=invite_id,
            evenement_id=evenement_id,
            statut=StatutInvitation.EN_ATTENTE.value,
        )
        self._session.add(invitation)
        try:
            self._session.flush()
        except IntegrityError as exc:
            erreur = self._traduire_integrite(exc, invite_id, evenement_id)
            if erreur is exc:
                raise
            raise erreur from exc
        return invitation

    @staticmethod
    def _traduire_integrite(exc: IntegrityError, invite_id: int, evenement_id: int) -> Exception:
        erreur = classer_erreur_integrite(exc)
        if erreur is ErreurIntegrite.ASSOCIATION_EN_DOUBLE:
            return ConflictError(
                "ASSOCIATION_EXISTANTE",
                f"L'invité {invite_id} est déjà associé à l'événement {evenement_id}.",
            )
        if erreur is ErreurIntegrite.INVITE_ABSENT:
            return NotFoundError("INVITE_INTROUVABLE", f"Invité {invite_id} introuvable.")
        if erreur is ErreurIntegrite.EVENEMENT_ABSENT:
            return NotFoundError("EVENEMENT_INTROUVABLE", f"Événement {evenement_id} introuvable.")
        return exc

    def delete_if_pristine(self, invitation: Invitation) -> bool:
        """Supprime l'association seulement si elle est encore vierge (non envoyée, EN_ATTENTE).

        Le ``DELETE`` est conditionnel, donc atomique : si un envoi ou une réponse a été enregistré
        entre la vérification du service et cette requête, rien n'est supprimé et la méthode
        retourne False (l'historique n'est jamais effacé par une course entre requêtes).
        """
        resultat = self._session.execute(
            delete(Invitation).where(
                Invitation.id == invitation.id,
                Invitation.date_envoi.is_(None),
                Invitation.statut == StatutInvitation.EN_ATTENTE.value,
            )
        )
        if resultat.rowcount != 1:
            return False
        self._session.expunge(invitation)
        return True

    def update_statut(self, invitation: Invitation, statut: StatutInvitation) -> Invitation:
        invitation.statut = StatutInvitation(statut).value
        self._session.flush()
        return invitation

    def marquer_envoyee(self, invitation: Invitation, date_envoi: datetime) -> bool:
        """Enregistre ``date_envoi`` seulement si elle est encore NULL.

        Retourne False si l'invitation a été marquée envoyée entre-temps (UPDATE conditionnel,
        donc sûr même avec des requêtes concurrentes).
        """
        resultat = self._session.execute(
            update(Invitation)
            .where(Invitation.id == invitation.id, Invitation.date_envoi.is_(None))
            .values(date_envoi=date_envoi)
        )
        if resultat.rowcount != 1:
            return False
        self._session.refresh(invitation)
        return True

    # ------------------------------------------------------------------ lecture unitaire

    def get(self, invitation_id: int) -> Invitation | None:
        return self._session.get(Invitation, invitation_id)

    def get_with_invite(self, invitation_id: int) -> Invitation | None:
        statement = (
            select(Invitation)
            .options(joinedload(Invitation.invite))
            .where(Invitation.id == invitation_id)
        )
        return self._session.scalars(statement).first()

    def get_by_invite_and_event(self, invite_id: int, evenement_id: int) -> Invitation | None:
        statement = select(Invitation).where(
            Invitation.invite_id == invite_id, Invitation.evenement_id == evenement_id
        )
        return self._session.scalars(statement).first()

    # ------------------------------------------------------------------ listes paginées

    def _page(self, statement: Select, count_statement: Select, limit: int, offset: int):
        total = self._session.scalar(count_statement) or 0
        lignes = self._session.execute(statement.limit(limit).offset(offset)).all()
        return lignes, total

    def list_invites_of_event(
        self, evenement_id: int, *, statut: StatutInvitation | None, limit: int, offset: int
    ) -> tuple[list[tuple[Invite, Invitation]], int]:
        conditions = [Invitation.evenement_id == evenement_id]
        if statut is not None:
            conditions.append(Invitation.statut == StatutInvitation(statut).value)
        statement = (
            select(Invite, Invitation)
            .join(Invitation, Invitation.invite_id == Invite.id)
            .where(*conditions)
            .order_by(Invitation.id)
        )
        count_statement = select(func.count(Invitation.id)).where(*conditions)
        lignes, total = self._page(statement, count_statement, limit, offset)
        return [(invite, invitation) for invite, invitation in lignes], total

    def list_for_event(
        self,
        evenement_id: int,
        *,
        statut: StatutInvitation | None,
        envoyee: bool | None,
        limit: int,
        offset: int,
    ) -> tuple[list[Invitation], int]:
        conditions = [Invitation.evenement_id == evenement_id]
        if statut is not None:
            conditions.append(Invitation.statut == StatutInvitation(statut).value)
        if envoyee is True:
            conditions.append(Invitation.date_envoi.is_not(None))
        elif envoyee is False:
            conditions.append(Invitation.date_envoi.is_(None))
        statement = (
            select(Invitation)
            .options(joinedload(Invitation.invite))
            .where(*conditions)
            .order_by(Invitation.id)
            .limit(limit)
            .offset(offset)
        )
        total = self._session.scalar(select(func.count(Invitation.id)).where(*conditions)) or 0
        return list(self._session.scalars(statement)), total

    def list_for_invite(
        self, invite_id: int, *, statut: StatutInvitation | None, limit: int, offset: int
    ) -> tuple[list[Invitation], int]:
        conditions = [Invitation.invite_id == invite_id]
        if statut is not None:
            conditions.append(Invitation.statut == StatutInvitation(statut).value)
        statement = (
            select(Invitation).where(*conditions).order_by(Invitation.id).limit(limit).offset(offset)
        )
        total = self._session.scalar(select(func.count(Invitation.id)).where(*conditions)) or 0
        return list(self._session.scalars(statement)), total

    # ------------------------------------------------------------------ statistiques

    def compter_par_statut(self, evenement_id: int) -> dict[str, int]:
        """Une seule requête agrégée (COUNT ... FILTER)."""
        statement = select(
            func.count(Invitation.id),
            func.count(Invitation.id).filter(Invitation.statut == StatutInvitation.EN_ATTENTE.value),
            func.count(Invitation.id).filter(Invitation.statut == StatutInvitation.ACCEPTEE.value),
            func.count(Invitation.id).filter(Invitation.statut == StatutInvitation.REFUSEE.value),
        ).where(Invitation.evenement_id == evenement_id)
        total, en_attente, acceptees, refusees = self._session.execute(statement).one()
        return {
            "total": total,
            "en_attente": en_attente,
            "acceptees": acceptees,
            "refusees": refusees,
        }
