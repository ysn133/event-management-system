"""Accès aux invités."""

from __future__ import annotations

from sqlalchemy import and_, delete, exists, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.exceptions import ConflictError
from app.models.invitation import Invitation
from app.models.invite import Invite
from app.repositories.errors import ErreurIntegrite, classer_erreur_integrite


def _motif_like(texte: str) -> str:
    """Motif ILIKE « contient » avec échappement de ``\\``, ``%`` et ``_`` saisis par l'utilisateur."""
    echappe = texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{echappe}%"


class InviteRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, *, nom: str, email: str | None, telephone: str | None) -> Invite:
        invite = Invite(nom=nom, email=email, telephone=telephone)
        self._session.add(invite)
        self._session.flush()
        return invite

    def get(self, invite_id: int) -> Invite | None:
        return self._session.get(Invite, invite_id)

    def list(self, *, limit: int, offset: int) -> tuple[list[Invite], int]:
        total = self._session.scalar(select(func.count(Invite.id))) or 0
        statement = select(Invite).order_by(Invite.id).limit(limit).offset(offset)
        return list(self._session.scalars(statement)), total

    def search(
        self,
        *,
        nom: str | None,
        email: str | None,
        telephone: str | None,
        limit: int,
        offset: int,
    ) -> tuple[list[Invite], int]:
        """Recherche partielle insensible à la casse ; les critères fournis se combinent en ET."""
        conditions = []
        if nom:
            conditions.append(Invite.nom.ilike(_motif_like(nom), escape="\\"))
        if email:
            conditions.append(Invite.email.ilike(_motif_like(email), escape="\\"))
        if telephone:
            conditions.append(Invite.telephone.ilike(_motif_like(telephone), escape="\\"))
        filtre = and_(*conditions)
        total = self._session.scalar(select(func.count(Invite.id)).where(filtre)) or 0
        statement = select(Invite).where(filtre).order_by(Invite.id).limit(limit).offset(offset)
        return list(self._session.scalars(statement)), total

    def update(self, invite: Invite, *, nom: str, email: str | None, telephone: str | None) -> Invite:
        invite.nom = nom
        invite.email = email
        invite.telephone = telephone
        self._session.flush()
        return invite

    def has_invitations(self, invite_id: int) -> bool:
        return bool(
            self._session.scalar(select(exists().where(Invitation.invite_id == invite_id)))
        )

    def delete(self, invite: Invite) -> bool:
        """Supprime l'invité seulement s'il n'a AUCUNE invitation ; retourne False sinon.

        Dans la base commune EventFlow, ``invitations.invite_id`` est ``ON DELETE CASCADE`` : la clé
        étrangère ne protégerait donc PAS l'historique et effacerait silencieusement les invitations.
        Le ``DELETE`` est par conséquent conditionnel (``NOT EXISTS``), pour qu'une invitation
        créée entre le contrôle du service et cette requête ne soit jamais supprimée en cascade.
        """
        try:
            resultat = self._session.execute(
                delete(Invite).where(
                    Invite.id == invite.id,
                    ~exists().where(Invitation.invite_id == Invite.id).correlate(Invite),
                )
            )
        except IntegrityError as exc:  # schéma SANS cascade : la FK refuse la suppression
            if classer_erreur_integrite(exc) is ErreurIntegrite.INVITE_ENCORE_REFERENCE:
                return False
            raise
        if resultat.rowcount != 1:
            return False
        self._session.expunge(invite)
        return True
