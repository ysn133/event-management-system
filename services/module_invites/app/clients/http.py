"""Appel HTTP GET commun aux clients des autres services de l'équipe (bibliothèque standard seule).

Traduction des échecs (jamais recopiés tels quels vers notre client) :

| Réponse de l'autre service         | Exception levée                                    | HTTP renvoyé |
|------------------------------------|----------------------------------------------------|--------------|
| 200                                | le JSON décodé                                     | -            |
| 404                                | ``None`` (à l'appelant d'en faire un 404 métier)   | -            |
| 401                                | ``AuthenticationRequiredError``                    | 401          |
| 403                                | ``ForbiddenError``                                 | 403          |
| 5xx, injoignable, délai dépassé    | ``ServiceUnavailableError``                        | 503          |
| autre 4xx, JSON invalide           | ``UpstreamError``                                  | 502          |

Le jeton JWT de l'appelant est retransmis dans ``Authorization: Bearer`` (comme le fait le service Budget).
"""

from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request
from typing import Any

from app.core.exceptions import (
    AuthenticationRequiredError,
    ForbiddenError,
    ServiceUnavailableError,
    UpstreamError,
)

logger = logging.getLogger(__name__)


def get_json(
    url: str,
    *,
    timeout: float,
    token: str | None,
    service: str,
    code_indisponible: str,
    code_invalide: str,
) -> Any | None:
    """GET ``url`` et retourne le JSON décodé, ou ``None`` sur 404 (voir le tableau ci-dessus)."""
    en_tetes = {"Accept": "application/json"}
    if token:
        en_tetes["Authorization"] = f"Bearer {token}"
    requete = urllib.request.Request(url, headers=en_tetes, method="GET")
    try:
        with urllib.request.urlopen(requete, timeout=timeout) as reponse:  # noqa: S310 - URL de configuration
            octets = reponse.read()
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        logger.error("Service %s : HTTP %s pour %s", service, exc.code, url)
        if exc.code == 401:
            raise AuthenticationRequiredError(
                "AUTHENTIFICATION_REQUISE",
                f"Le service {service} exige une authentification : fournissez un jeton JWT valide.",
            ) from exc
        if exc.code == 403:
            raise ForbiddenError("ACCES_SERVICE_REFUSE", f"Le service {service} a refusé l'accès.") from exc
        if exc.code >= 500:
            raise ServiceUnavailableError(code_indisponible, f"Le service {service} est indisponible.") from exc
        raise UpstreamError(code_invalide, f"Le service {service} a refusé la requête.") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        logger.error("Service %s injoignable (%s) : %s", service, url, exc)
        raise ServiceUnavailableError(code_indisponible, f"Le service {service} est injoignable.") from exc
    try:
        return json.loads(octets)
    except ValueError as exc:
        raise UpstreamError(code_invalide, f"Le service {service} a renvoyé une réponse inattendue.") from exc
