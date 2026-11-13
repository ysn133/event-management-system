"""Documentation OpenAPI des réponses d'erreur (format unique ``ErrorResponse``)."""

from typing import Any

from app.schemas.common import ErrorResponse

_DESCRIPTIONS = {
    400: "Requête invalide (ex. aucun critère de recherche).",
    401: "Authentification requise ou non configurée.",
    403: "Accès refusé à cet événement.",
    404: "Ressource introuvable.",
    409: "Conflit avec l'état actuel (doublon, suppression interdite, déjà envoyée...).",
    422: "Données invalides.",
    500: "Erreur interne (aucun détail technique n'est exposé).",
    502: "Le fournisseur d'envoi a échoué ou n'a pas confirmé l'envoi.",
    503: "Aucun fournisseur d'envoi n'est configuré.",
}


def reponses(*codes: int) -> dict[int | str, dict[str, Any]]:
    """Construit le paramètre ``responses`` d'une route pour les codes d'erreur donnés."""
    return {code: {"model": ErrorResponse, "description": _DESCRIPTIONS[code]} for code in codes}
