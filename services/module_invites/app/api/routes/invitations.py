"""Routes des invitations : envoi, consultation et suivi des réponses."""

from fastapi import APIRouter

from app.api.dependencies import Identifiant, InvitationServiceDep, PaginationDep
from app.api.responses import reponses
from app.core.enums import StatutInvitation
from app.schemas.common import Page
from app.schemas.invitation import (
    EnvoiResultat,
    InvitationCreate,
    InvitationDetailRead,
    InvitationEnvoiRead,
    InvitationRead,
    StatutUpdate,
)

router = APIRouter(tags=["Invitations"])


@router.post(
    "/evenements/{evenement_id}/invitations",
    response_model=InvitationEnvoiRead,
    status_code=200,
    summary="Envoyer l'invitation d'un invité associé à l'événement",
    description=(
        "Déclenche l'envoi pour une association EXISTANTE (200 si l'envoi est confirmé, "
        "``date_envoi`` est alors enregistrée). Aucune ligne n'est créée par cette route. "
        "Si le service est une simulation, ``envoi.simulation`` vaut ``true`` : aucun message réel "
        "n'a été envoyé. 409 : invité non associé ou invitation déjà envoyée ; "
        "502 : échec du fournisseur ; 503 : aucun fournisseur configuré."
    ),
    responses=reponses(401, 403, 404, 409, 422, 500, 502, 503),
)
def envoyer_invitation(evenement_id: Identifiant, data: InvitationCreate, service: InvitationServiceDep):
    invitation, resultat = service.envoyer(evenement_id, data)
    return InvitationEnvoiRead(
        invitation=InvitationRead.model_validate(invitation),
        envoi=EnvoiResultat(
            confirme=resultat.confirme,
            canal=resultat.canal,
            fournisseur=resultat.fournisseur,
            simulation=resultat.simulation,
        ),
    )


@router.get(
    "/invitations/{invitation_id}",
    response_model=InvitationDetailRead,
    summary="Consulter une invitation",
    responses=reponses(401, 403, 404, 422, 500),
)
def obtenir_invitation(invitation_id: Identifiant, service: InvitationServiceDep):
    return InvitationDetailRead.model_validate(service.obtenir(invitation_id))


@router.get(
    "/evenements/{evenement_id}/invitations",
    response_model=Page[InvitationDetailRead],
    summary="Lister les invitations d'un événement",
    description="Filtres : ``statut`` (réponse) et ``envoyee`` (true/false, d'après ``date_envoi``).",
    responses=reponses(401, 403, 404, 422, 500),
)
def lister_invitations_evenement(
    evenement_id: Identifiant,
    service: InvitationServiceDep,
    pagination: PaginationDep,
    statut: StatutInvitation | None = None,
    envoyee: bool | None = None,
):
    items, total = service.lister_evenement(
        evenement_id,
        statut=statut,
        envoyee=envoyee,
        limit=pagination.limit,
        offset=pagination.offset,
    )
    return Page[InvitationDetailRead](
        items=[InvitationDetailRead.model_validate(item) for item in items],
        total=total,
        limit=pagination.limit,
        offset=pagination.offset,
    )


@router.get(
    "/invites/{invite_id}/invitations",
    response_model=Page[InvitationRead],
    summary="Lister les invitations d'un invité",
    responses=reponses(401, 404, 422, 500),
)
def lister_invitations_invite(
    invite_id: Identifiant,
    service: InvitationServiceDep,
    pagination: PaginationDep,
    statut: StatutInvitation | None = None,
):
    items, total = service.lister_invite(
        invite_id, statut=statut, limit=pagination.limit, offset=pagination.offset
    )
    return Page[InvitationRead](
        items=[InvitationRead.model_validate(item) for item in items],
        total=total,
        limit=pagination.limit,
        offset=pagination.offset,
    )


@router.patch(
    "/invitations/{invitation_id}/statut",
    response_model=InvitationRead,
    summary="Enregistrer la réponse d'un invité",
    description=(
        "Statuts autorisés : EN_ATTENTE, ACCEPTEE, REFUSEE. Toute transition est permise "
        "(l'organisateur peut corriger une erreur ou saisir une réponse reçue hors application)."
    ),
    responses=reponses(401, 403, 404, 422, 500),
)
def changer_statut_invitation(invitation_id: Identifiant, data: StatutUpdate, service: InvitationServiceDep):
    return InvitationRead.model_validate(service.changer_statut(invitation_id, data.statut))
