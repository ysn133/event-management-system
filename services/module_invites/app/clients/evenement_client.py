"""Client HTTP du service Événements (développé par un autre membre de l'équipe).
"""

from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from app.core.exceptions import ServiceUnavailableError, UpstreamError

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class EvenementInfo:
    """Les champs du service Événements dont le module a besoin (noms en snake_case)."""

    id: int
    organisateur_id: int
    titre: str
    description: str | None
    date_debut: datetime
    date_fin: datetime
    lieu: str | None
    statut: str


def _date(valeur: Any) -> datetime:
    return datetime.fromisoformat(str(valeur).replace("Z", "+00:00"))


def lire_evenement(corps: Any) -> EvenementInfo:
    """Convertit le JSON du service ; lève ``UpstreamError`` s'il ne respecte pas le contrat."""
    try:
        return EvenementInfo(
            id=int(corps["id"]),
            organisateur_id=int(corps["organisateurId"]),
            titre=str(corps["titre"]),
            description=corps.get("description"),
            date_debut=_date(corps["dateDebut"]),
            date_fin=_date(corps["dateFin"]),
            lieu=corps.get("lieu"),
            statut=str(corps["statut"]),
        )
    except (KeyError, TypeError, ValueError, AttributeError) as exc:
        raise UpstreamError(
            "REPONSE_EVENEMENTS_INVALIDE", "Le service Événements a renvoyé une réponse inattendue."
        ) from exc


class EvenementClient:
    def __init__(self, base_url: str, timeout: float = 5.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout

    def get(self, evenement_id: int) -> EvenementInfo | None:
        url = f"{self._base_url}/api/evenements/{int(evenement_id)}"
        requete = urllib.request.Request(url, headers={"Accept": "application/json"}, method="GET")
        try:
            with urllib.request.urlopen(requete, timeout=self._timeout) as reponse:  # noqa: S310 - URL de configuration
                octets = reponse.read()
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return None
            logger.error("Service Événements : HTTP %s pour %s", exc.code, url)
            if exc.code >= 500:
                raise ServiceUnavailableError(
                    "SERVICE_EVENEMENTS_INDISPONIBLE", "Le service Événements est indisponible."
                ) from exc
            raise UpstreamError(
                "REPONSE_EVENEMENTS_INVALIDE", "Le service Événements a refusé la requête."
            ) from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            logger.error("Service Événements injoignable (%s) : %s", url, exc)
            raise ServiceUnavailableError(
                "SERVICE_EVENEMENTS_INDISPONIBLE", "Le service Événements est injoignable."
            ) from exc
        try:
            corps = json.loads(octets)
        except ValueError as exc:
            raise UpstreamError(
                "REPONSE_EVENEMENTS_INVALIDE", "Le service Événements a renvoyé une réponse inattendue."
            ) from exc
        return lire_evenement(corps)
