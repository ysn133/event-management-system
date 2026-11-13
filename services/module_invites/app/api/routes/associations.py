"""Routes d'association invité ↔ événement."""

from fastapi import APIRouter, Response

from app.api.dependencies import AssociationServiceDep, Identifiant, PaginationDep
from app.api.responses import reponses
from app.core.enums import StatutInvitation
from app.schemas.association import EvenementAssocieRead, InviteAssocieRead
from app.schemas.common import Page
from app.schemas.invitation import InvitationRead

router = APIRouter(tags=["Associations événement-invité"])


@router.post(
    "/evenements/{evenement_id}/invites/{invite_id}",
    response_model=InvitationRead,
    status_code=201,
    summary="Associer un invité à un événement",
    description=(
        "Crée une invitation EN_ATTENTE, non envoyée (``date_envoi`` nulle). "
        "L'envoi se fait ensuite via ``POST /evenements/{id}/invitations``."
    ),
    responses=reponses(401, 403, 404, 409, 422, 500),
)
def associer_invite(evenement_id: Identifiant, invite_id: Identifiant, service: AssociationServiceDep):
    return service.associer(evenement_id, invite_id)


@router.get(
    "/evenements/{evenement_id}/invites",
    response_model=Page[InviteAssocieRead],
    summary="Lister les invités associés à un événement",
    responses=reponses(401, 403, 404, 422, 500),
)
def lister_invites_evenement(
    evenement_id: Identifiant,
    service: AssociationServiceDep,
    pagination: PaginationDep,
    statut: StatutInvitation | None = None,
):
    lignes, total = service.invites_de_evenement(
        evenement_id, statut=statut, limit=pagination.limit, offset=pagination.offset
    )
    return Page[InviteAssocieRead](
        items=[InviteAssocieRead.depuis(invite, invitation) for invite, invitation in lignes],
        total=total,
        limit=pagination.limit,
        offset=pagination.offset,
    )


@router.get(
    "/invites/{invite_id}/evenements",
    response_model=Page[EvenementAssocieRead],
    summary="Lister les événements auxquels un invité est associé",
    responses=reponses(401, 404, 422, 500),
)
def lister_evenements_invite(
    invite_id: Identifiant,
    service: AssociationServiceDep,
    pagination: PaginationDep,
    statut: StatutInvitation | None = None,
):
    lignes, total = service.evenements_de_invite(
        invite_id, statut=statut, limit=pagination.limit, offset=pagination.offset
    )
    return Page[EvenementAssocieRead](
        items=[EvenementAssocieRead.depuis(evenement, invitation) for evenement, invitation in lignes],
        total=total,
        limit=pagination.limit,
        offset=pagination.offset,
    )


@router.delete(
    "/evenements/{evenement_id}/invites/{invite_id}",
    status_code=204,
    response_class=Response,
    summary="Supprimer l'association invité ↔ événement",
    description=(
        "Refusé (409) si l'invitation a déjà été envoyée ou a reçu une réponse "
        "(l'historique est conservé)."
    ),
    responses=reponses(401, 403, 404, 409, 422, 500),
)
def dissocier_invite(
    evenement_id: Identifiant, invite_id: Identifiant, service: AssociationServiceDep
) -> Response:
    service.dissocier(evenement_id, invite_id)
    return Response(status_code=204)
