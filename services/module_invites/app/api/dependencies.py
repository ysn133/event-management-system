"""Dépendances FastAPI : session, pagination, identité, politique d'accès et services.

Tout est lu depuis ``request.app.state`` (rempli par ``create_app``), ce qui permet de créer
plusieurs applications indépendantes (tests, intégration dans le projet principal).
"""

from collections.abc import Iterator
from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Path, Query, Request
from sqlalchemy.orm import Session

from app.clients.evenement_client import EvenementClient
from app.core.security import AccessPolicy, CurrentUser, resolve_user
from app.repositories.invitation_repository import InvitationRepository
from app.repositories.invite_repository import InviteRepository
from app.schemas.invitation import IDENTIFIANT_MAX
from app.services.association_service import AssociationService
from app.services.invitation_service import InvitationService
from app.services.invite_service import InviteService
from app.services.notification_service import NotificationSender
from app.services.statistiques_service import StatistiquesService

# Identifiant de ressource : entier strictement positif, tenant dans un bigint PostgreSQL.
Identifiant = Annotated[int, Path(gt=0, le=IDENTIFIANT_MAX)]


@dataclass(frozen=True)
class Pagination:
    limit: int
    offset: int


def get_pagination(
    limit: Annotated[int, Query(ge=1, le=100, description="Nombre d'éléments par page (1 à 100).")] = 20,
    offset: Annotated[int, Query(ge=0, description="Nombre d'éléments à ignorer.")] = 0,
) -> Pagination:
    return Pagination(limit=limit, offset=offset)


def get_db(request: Request) -> Iterator[Session]:
    """Une session par requête ; fermée (donc annulée si non validée) dans tous les cas."""
    session = request.app.state.session_factory()
    try:
        yield session
    finally:
        session.close()


def require_user(request: Request) -> CurrentUser | None:
    """Identifie l'appelant (voir ``app/core/security.py``). Appliquée à toutes les routes /api/v1."""
    return resolve_user(request, request.app.state.settings.auth_enabled)


def get_evenements(request: Request) -> EvenementClient:
    return request.app.state.evenements


def get_sender(request: Request) -> NotificationSender:
    return request.app.state.sender


SessionDep = Annotated[Session, Depends(get_db)]
PaginationDep = Annotated[Pagination, Depends(get_pagination)]
CurrentUserDep = Annotated[CurrentUser | None, Depends(require_user)]
EvenementsDep = Annotated[EvenementClient, Depends(get_evenements)]
SenderDep = Annotated[NotificationSender, Depends(get_sender)]


def get_access_policy(request: Request, user: CurrentUserDep) -> AccessPolicy:
    return AccessPolicy(enabled=request.app.state.settings.organiser_check_enabled, user=user)


AccessPolicyDep = Annotated[AccessPolicy, Depends(get_access_policy)]


def get_invite_service(session: SessionDep) -> InviteService:
    return InviteService(session, InviteRepository(session))


def get_association_service(
    session: SessionDep, evenements: EvenementsDep, access: AccessPolicyDep
) -> AssociationService:
    return AssociationService(
        session,
        InviteRepository(session),
        evenements,
        InvitationRepository(session),
        access,
    )


def get_invitation_service(
    session: SessionDep, evenements: EvenementsDep, sender: SenderDep, access: AccessPolicyDep
) -> InvitationService:
    return InvitationService(
        session,
        InviteRepository(session),
        evenements,
        InvitationRepository(session),
        sender,
        access,
    )


def get_statistiques_service(
    session: SessionDep, evenements: EvenementsDep, access: AccessPolicyDep
) -> StatistiquesService:
    return StatistiquesService(evenements, InvitationRepository(session), access)


InviteServiceDep = Annotated[InviteService, Depends(get_invite_service)]
AssociationServiceDep = Annotated[AssociationService, Depends(get_association_service)]
InvitationServiceDep = Annotated[InvitationService, Depends(get_invitation_service)]
StatistiquesServiceDep = Annotated[StatistiquesService, Depends(get_statistiques_service)]
