"""Route des statistiques d'invitations."""

from fastapi import APIRouter

from app.api.dependencies import Identifiant, StatistiquesServiceDep
from app.api.responses import reponses
from app.schemas.statistiques import StatistiquesInvitations

router = APIRouter(tags=["Statistiques"])


@router.get(
    "/evenements/{evenement_id}/statistiques-invitations",
    response_model=StatistiquesInvitations,
    summary="Statistiques des invitations d'un événement",
    description=(
        "Totaux par statut et pourcentages d'acceptation et de refus (sur le total des invitations, "
        "arrondis à 2 décimales ; 0 si l'événement n'a aucune invitation)."
    ),
    responses=reponses(401, 403, 404, 422, 500),
)
def statistiques_invitations(evenement_id: Identifiant, service: StatistiquesServiceDep):
    return service.pour_evenement(evenement_id)
