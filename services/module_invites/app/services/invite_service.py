"""Cas d'usage de gestion des invités."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from app.core.exceptions import BadRequestError, ConflictError, NotFoundError
from app.schemas.invite import InviteCreate, InviteUpdate
from app.services.base import BaseService, SessionLike

if TYPE_CHECKING:
    from app.repositories.invite_repository import InviteRepository


class InviteService(BaseService):
    def __init__(self, session: SessionLike, invites: InviteRepository) -> None:
        super().__init__(session)
        self._invites = invites

    def _obtenir_ou_404(self, invite_id: int) -> Any:
        invite = self._invites.get(invite_id)
        if invite is None:
            raise NotFoundError("INVITE_INTROUVABLE", f"Invité {invite_id} introuvable.")
        return invite

    def creer(self, data: InviteCreate) -> Any:
        with self.transaction():
            return self._invites.add(nom=data.nom, email=data.email, telephone=data.telephone)

    def lister(self, *, limit: int, offset: int) -> tuple[list[Any], int]:
        return self._invites.list(limit=limit, offset=offset)

    def obtenir(self, invite_id: int) -> Any:
        return self._obtenir_ou_404(invite_id)

    def modifier(self, invite_id: int, data: InviteUpdate) -> Any:
        invite = self._obtenir_ou_404(invite_id)
        with self.transaction():
            return self._invites.update(
                invite, nom=data.nom, email=data.email, telephone=data.telephone
            )

    def supprimer(self, invite_id: int) -> None:
        invite = self._obtenir_ou_404(invite_id)
        if self._invites.has_invitations(invite_id):
            raise ConflictError(
                "INVITE_AVEC_INVITATIONS",
                "Impossible de supprimer cet invité : des invitations lui sont associées.",
            )
        with self.transaction():
            if not self._invites.delete(invite):  # invitation créée entre-temps (course)
                raise ConflictError(
                    "INVITE_AVEC_INVITATIONS",
                    "Impossible de supprimer cet invité : des invitations lui sont associées.",
                )

    def rechercher(
        self,
        *,
        nom: str | None,
        email: str | None,
        telephone: str | None,
        limit: int,
        offset: int,
    ) -> tuple[list[Any], int]:
        nom = (nom or "").strip() or None
        email = (email or "").strip() or None
        telephone = (telephone or "").strip() or None
        if nom is None and email is None and telephone is None:
            raise BadRequestError(
                "CRITERE_RECHERCHE_MANQUANT",
                "Au moins un critère de recherche est obligatoire : nom, email ou telephone.",
            )
        return self._invites.search(
            nom=nom, email=email, telephone=telephone, limit=limit, offset=offset
        )
