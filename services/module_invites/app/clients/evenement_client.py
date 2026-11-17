"""Client HTTP du service Événements (développé par un autre membre de l'équipe).

Le module Invités NE lit PLUS la table ``evenements`` : il interroge le service qui en est propriétaire,
``GET {EVENEMENTS_SERVICE_URL}/api/evenements/{id}``. Seule la bibliothèque standard est utilisée.

Contrat attendu (``EvenementResponse`` du service Java) :
``{"id", "organisateurId", "titre", "description", "dateDebut", "dateFin", "lieu", "statut"}``
avec des dates ISO 8601. Les erreurs du service ne sont jamais recopiées vers notre client.

Résultat de ``get`` : un ``EvenementInfo``, ou ``None`` si le service répond 404.
Les échecs sont traduits par ``app/clients/http.py`` (401, 403, 502, 503). Le jeton JWT de l'appelant est
retransmis (``with_token``).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any

from app.clients.http import get_json
from app.core.exceptions import UpstreamError


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
    def __init__(self, base_url: str, timeout: float = 5.0, token: str | None = None) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout
        self._token = token

    def with_token(self, token: str | None) -> EvenementClient:
        """Copie liée au jeton JWT de l'appelant (le service Événements exige une authentification)."""
        return EvenementClient(self._base_url, self._timeout, token)

    def get(self, evenement_id: int) -> EvenementInfo | None:
        corps = get_json(
            f"{self._base_url}/api/evenements/{int(evenement_id)}",
            timeout=self._timeout,
            token=self._token,
            service="Événements",
            code_indisponible="SERVICE_EVENEMENTS_INDISPONIBLE",
            code_invalide="REPONSE_EVENEMENTS_INVALIDE",
        )
        return None if corps is None else lire_evenement(corps)
