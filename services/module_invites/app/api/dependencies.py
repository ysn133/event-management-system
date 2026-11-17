"""Dépendances FastAPI : session, pagination, identité, politique d'accès et services.

Tout est lu depuis ``request.app.state`` (rempli par ``create_app``), ce qui permet de créer
plusieurs applications indépendantes (tests, intégration dans le projet principal).
"""

from collections.abc import Iterator
from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Path, Query, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.clients.evenement_client import EvenementClient
from app.clients.salle_client import SalleClient
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


# auto_error=False : l'authentification reste facultative en local ; le schéma sert à Swagger (« Authorize »).
_bearer = HTTPBearer(auto_error=False, description="Jeton obtenu auprès du service Auth (POST /api/auth/login).")


def get_bearer_token(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
) -> str | None:
    return credentials.credentials if credentials else None


def require_user(request: Request, _schema: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)] = None) -> CurrentUser | None:
    """Identifie l'appelant (voir ``app/core/security.py``). Appliquée à toutes les routes /api/v1."""
    return resolve_user(request, request.app.state.settings.auth_enabled)


def _avec_jeton(client, token: str | None):
    """Retransmet le jeton de l'appelant aux services de l'équipe (les doublures de test n'en ont pas)."""
    return client.with_token(token) if token and hasattr(client, "with_token") else client


def get_evenements(request: Request, token: Annotated[str | None, Depends(get_bearer_token)]) -> EvenementClient:
    return _avec_jeton(request.app.state.evenements, token)


def get_salles(request: Request, token: Annotated[str | None, Depends(get_bearer_token)]) -> SalleClient:
    return _avec_jeton(request.app.state.salles, token)


def get_sender(request: Request) -> NotificationSender:
    return request.app.state.sender


SessionDep = Annotated[Session, Depends(get_db)]
PaginationDep = Annotated[Pagination, Depends(get_pagination)]
CurrentUserDep = Annotated[CurrentUser | None, Depends(require_user)]
EvenementsDep = Annotated[EvenementClient, Depends(get_evenements)]
SallesDep = Annotated[SalleClient, Depends(get_salles)]
SenderDep = Annotated[NotificationSender, Depends(get_sender)]


def get_access_policy(request: Request, user: CurrentUserDep) -> AccessPolicy:
    return AccessPolicy(enabled=request.app.state.settings.organiser_check_enabled, user=user)


AccessPolicyDep = Annotated[AccessPolicy, Depends(get_access_policy)]


def get_invite_service(session: SessionDep) -> InviteService:
    return InviteService(session, InviteRepository(session))


def get_association_service(
    session: SessionDep, evenements: EvenementsDep, salles: SallesDep, access: AccessPolicyDep
) -> AssociationService:
    return AssociationService(
        session,
        InviteRepository(session),
        evenements,
        InvitationRepository(session),
        salles,
        access,
    )


def get_invitation_service(
    session: SessionDep,
    evenements: EvenementsDep,
    salles: SallesDep,
    sender: SenderDep,
    access: AccessPolicyDep,
) -> InvitationService:
    return InvitationService(
        session,
        InviteRepository(session),
        evenements,
        InvitationRepository(session),
        sender,
        salles,
        access,
    )


def get_statistiques_service(
    session: SessionDep, evenements: EvenementsDep, salles: SallesDep, access: AccessPolicyDep
) -> StatistiquesService:
    return StatistiquesService(evenements, InvitationRepository(session), salles, access)


InviteServiceDep = Annotated[InviteService, Depends(get_invite_service)]
AssociationServiceDep = Annotated[AssociationService, Depends(get_association_service)]
InvitationServiceDep = Annotated[InvitationService, Depends(get_invitation_service)]
StatistiquesServiceDep = Annotated[StatistiquesService, Depends(get_statistiques_service)]
