"""Routes de gestion des invités."""

from typing import Annotated

from fastapi import APIRouter, Query, Response

from app.api.dependencies import Identifiant, InviteServiceDep, PaginationDep
from app.api.responses import reponses
from app.schemas.common import Page
from app.schemas.invite import InviteCreate, InviteRead, InviteUpdate

router = APIRouter(prefix="/invites", tags=["Invités"])


def _page(items: list, total: int, pagination) -> Page[InviteRead]:
    return Page[InviteRead](
        items=[InviteRead.model_validate(item) for item in items],
        total=total,
        limit=pagination.limit,
        offset=pagination.offset,
    )


@router.post(
    "",
    response_model=InviteRead,
    status_code=201,
    summary="Ajouter un invité",
    responses=reponses(401, 422, 500),
)
def creer_invite(data: InviteCreate, service: InviteServiceDep):
    return service.creer(data)


@router.get(
    "",
    response_model=Page[InviteRead],
    summary="Lister les invités (paginé)",
    responses=reponses(401, 422, 500),
)
def lister_invites(service: InviteServiceDep, pagination: PaginationDep):
    items, total = service.lister(limit=pagination.limit, offset=pagination.offset)
    return _page(items, total, pagination)


# Déclarée AVANT « /{invite_id} » pour que « search » ne soit pas lu comme un identifiant.
@router.get(
    "/search",
    response_model=Page[InviteRead],
    summary="Rechercher des invités par nom, e-mail ou téléphone",
    description=(
        "Recherche partielle insensible à la casse. Les critères fournis se combinent (ET). "
        "Au moins un critère est obligatoire."
    ),
    responses=reponses(400, 401, 422, 500),
)
def rechercher_invites(
    service: InviteServiceDep,
    pagination: PaginationDep,
    nom: Annotated[str | None, Query(max_length=255)] = None,
    email: Annotated[str | None, Query(max_length=254)] = None,
    telephone: Annotated[str | None, Query(max_length=20)] = None,
):
    items, total = service.rechercher(
        nom=nom,
        email=email,
        telephone=telephone,
        limit=pagination.limit,
        offset=pagination.offset,
    )
    return _page(items, total, pagination)


@router.get(
    "/{invite_id}",
    response_model=InviteRead,
    summary="Afficher un invité",
    responses=reponses(401, 404, 422, 500),
)
def obtenir_invite(invite_id: Identifiant, service: InviteServiceDep):
    return service.obtenir(invite_id)


@router.put(
    "/{invite_id}",
    response_model=InviteRead,
    summary="Modifier un invité (remplacement complet)",
    responses=reponses(401, 404, 422, 500),
)
def modifier_invite(invite_id: Identifiant, data: InviteUpdate, service: InviteServiceDep):
    return service.modifier(invite_id, data)


@router.delete(
    "/{invite_id}",
    status_code=204,
    response_class=Response,
    summary="Supprimer un invité",
    description="Refusé (409) si des invitations sont associées à cet invité.",
    responses=reponses(401, 404, 409, 422, 500),
)
def supprimer_invite(invite_id: Identifiant, service: InviteServiceDep) -> Response:
    service.supprimer(invite_id)
    return Response(status_code=204)
