"""Dépôts et session FACTICES en mémoire pour les tests unitaires des services.

Ils ne testent PAS PostgreSQL ni SQLAlchemy : seulement la logique des services.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import Any

from app.core.enums import StatutInvitation
from app.core.exceptions import ConflictError
from app.services.notification_service import SimulatedNotificationSender

HORLOGE_FIXE = datetime(2026, 11, 7, 12, 0, tzinfo=UTC)


class FakeSession:
    def __init__(self) -> None:
        self.commits = 0
        self.rollbacks = 0

    def commit(self) -> None:
        self.commits += 1

    def rollback(self) -> None:
        self.rollbacks += 1


class FakeInviteRepository:
    def __init__(self) -> None:
        self.items: dict[int, SimpleNamespace] = {}
        self.invitations: FakeInvitationRepository | None = None
        self._sequence = 0

    def add(self, *, nom: str, email: str | None, telephone: str | None) -> SimpleNamespace:
        self._sequence += 1
        invite = SimpleNamespace(id=self._sequence, nom=nom, email=email, telephone=telephone)
        self.items[invite.id] = invite
        return invite

    def get(self, invite_id: int) -> SimpleNamespace | None:
        return self.items.get(invite_id)

    def list(self, *, limit: int, offset: int):
        tous = sorted(self.items.values(), key=lambda i: i.id)
        return tous[offset : offset + limit], len(tous)

    def search(self, *, nom, email, telephone, limit, offset):
        resultats = [
            i
            for i in sorted(self.items.values(), key=lambda i: i.id)
            if (not nom or nom.lower() in i.nom.lower())
            and (not email or (i.email and email.lower() in i.email.lower()))
            and (not telephone or (i.telephone and telephone in i.telephone))
        ]
        return resultats[offset : offset + limit], len(resultats)

    def update(self, invite, *, nom, email, telephone):
        invite.nom, invite.email, invite.telephone = nom, email, telephone
        return invite

    def has_invitations(self, invite_id: int) -> bool:
        return any(i.invite_id == invite_id for i in self.invitations.items.values())

    def delete(self, invite) -> bool:
        del self.items[invite.id]
        return True


class FakeEvenementClient:
    """Remplace le client HTTP du service Événements."""

    def __init__(self) -> None:
        self.items: dict[int, SimpleNamespace] = {}
        self._sequence = 0

    def ajouter(self, *, titre: str = "Conférence", organisateur_id: int = 1) -> SimpleNamespace:
        self._sequence += 1
        evenement = SimpleNamespace(
            id=self._sequence,
            organisateur_id=organisateur_id,
            titre=titre,
            lieu="Salle A",
            date_debut=HORLOGE_FIXE + timedelta(days=7),
            date_fin=HORLOGE_FIXE + timedelta(days=7, hours=2),
            statut="PLANIFIE",
        )
        self.items[evenement.id] = evenement
        return evenement

    def get(self, evenement_id: int) -> SimpleNamespace | None:
        return self.items.get(evenement_id)


class FakeInvitationRepository:
    def __init__(self, invites: FakeInviteRepository) -> None:
        self.items: dict[int, SimpleNamespace] = {}
        self._invites = invites
        self._sequence = 0

    def add(self, invite_id: int, evenement_id: int) -> SimpleNamespace:
        # Imite la contrainte UNIQUE de PostgreSQL : vérifie directement le contenu stocké
        # (indépendamment de get_by_invite_and_event, que certains tests remplacent).
        if any(
            i.invite_id == invite_id and i.evenement_id == evenement_id for i in self.items.values()
        ):
            raise ConflictError("ASSOCIATION_EXISTANTE", "doublon")
        self._sequence += 1
        invitation = SimpleNamespace(
            id=self._sequence,
            invite_id=invite_id,
            evenement_id=evenement_id,
            date_envoi=None,
            statut=StatutInvitation.EN_ATTENTE.value,
        )
        self.items[invitation.id] = invitation
        return invitation

    def get(self, invitation_id: int) -> SimpleNamespace | None:
        return self.items.get(invitation_id)

    def get_with_invite(self, invitation_id: int) -> SimpleNamespace | None:
        invitation = self.items.get(invitation_id)
        if invitation is not None:
            invitation.invite = self._invites.items[invitation.invite_id]
        return invitation

    def get_by_invite_and_event(self, invite_id: int, evenement_id: int):
        for invitation in self.items.values():
            if invitation.invite_id == invite_id and invitation.evenement_id == evenement_id:
                return invitation
        return None

    def delete_if_pristine(self, invitation) -> bool:
        if invitation.date_envoi is not None or invitation.statut != StatutInvitation.EN_ATTENTE.value:
            return False
        del self.items[invitation.id]
        return True

    def update_statut(self, invitation, statut):
        invitation.statut = StatutInvitation(statut).value
        return invitation

    def marquer_envoyee(self, invitation, date_envoi: datetime) -> bool:
        if invitation.date_envoi is not None:
            return False
        invitation.date_envoi = date_envoi
        return True

    def list_invites_of_event(self, evenement_id, *, statut, limit, offset):
        lignes = [
            (self._invites.items[i.invite_id], i)
            for i in sorted(self.items.values(), key=lambda i: i.id)
            if i.evenement_id == evenement_id and (statut is None or i.statut == statut.value)
        ]
        return lignes[offset : offset + limit], len(lignes)

    def list_for_event(self, evenement_id, *, statut, envoyee, limit, offset):
        items = [
            i
            for i in sorted(self.items.values(), key=lambda i: i.id)
            if i.evenement_id == evenement_id
            and (statut is None or i.statut == statut.value)
            and (envoyee is None or (i.date_envoi is not None) == envoyee)
        ]
        return items[offset : offset + limit], len(items)

    def list_for_invite(self, invite_id, *, statut, limit, offset):
        items = [
            i
            for i in sorted(self.items.values(), key=lambda i: i.id)
            if i.invite_id == invite_id and (statut is None or i.statut == statut.value)
        ]
        return items[offset : offset + limit], len(items)

    def compter_par_statut(self, evenement_id: int) -> dict[str, int]:
        statuts = [i.statut for i in self.items.values() if i.evenement_id == evenement_id]
        return {
            "total": len(statuts),
            "en_attente": statuts.count("EN_ATTENTE"),
            "acceptees": statuts.count("ACCEPTEE"),
            "refusees": statuts.count("REFUSEE"),
        }


class Monde:
    """Assemble session, dépôts factices et service d'envoi simulé."""

    def __init__(self) -> None:
        self.session = FakeSession()
        self.invites = FakeInviteRepository()
        self.evenements = FakeEvenementClient()
        self.invitations = FakeInvitationRepository(self.invites)
        self.invites.invitations = self.invitations
        self.sender = SimulatedNotificationSender()

    def inviter(self, nom: str = "Amine", email: str | None = "amine@example.com", telephone=None) -> Any:
        return self.invites.add(nom=nom, email=email, telephone=telephone)
