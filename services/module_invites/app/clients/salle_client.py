"""Client HTTP du service Salles (ASP.NET Core, développé par un autre membre de l'équipe).

Sert à connaître la CAPACITÉ disponible d'un événement (les invités ne doivent pas dépasser la capacité
de la salle réservée). Deux routes sont utilisées :

- ``GET /api/reservations/evenement/{id}`` -> ``[{"salleId", "statut", "nomSalle", ...}]`` ;
- ``GET /api/salles/{id}``                 -> ``{"id", "nom", "capacite", ...}``.

Règles retenues (à valider par l'équipe) :

- les réservations ``ANNULEE`` sont ignorées ; ``EN_ATTENTE`` et ``CONFIRMEE`` comptent ;
- si l'événement a plusieurs salles réservées, la capacité est la SOMME des capacités des salles distinctes
  (une même salle réservée deux fois n'est comptée qu'une fois) ;
- aucune salle réservée -> capacité inconnue (``total`` vaut ``None``) : aucune limite n'est appliquée.

Échecs (service arrêté, 5xx, 401...) : voir ``app/clients/http.py`` ; ils ne sont JAMAIS ignorés en silence.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from app.clients.http import get_json
from app.core.exceptions import UpstreamError

logger = logging.getLogger(__name__)

STATUT_ANNULE = "ANNULEE"


@dataclass(frozen=True)
class SalleReservee:
    id: int
    nom: str | None
    capacite: int


@dataclass(frozen=True)
class CapaciteEvenement:
    """Salles réservées pour un événement. ``total`` est ``None`` s'il n'y en a aucune."""

    salles: tuple[SalleReservee, ...] = ()

    @property
    def total(self) -> int | None:
        return sum(salle.capacite for salle in self.salles) if self.salles else None


def _invalide() -> UpstreamError:
    return UpstreamError("REPONSE_SALLES_INVALIDE", "Le service Salles a renvoyé une réponse inattendue.")


def lire_salles_reservees(reservations: Any) -> list[tuple[int, str | None]]:
    """Identifiants (et noms) des salles de réservations non annulées, sans doublon, dans l'ordre."""
    if not isinstance(reservations, list):
        raise _invalide()
    vues: dict[int, str | None] = {}
    try:
        for reservation in reservations:
            if str(reservation["statut"]).upper() == STATUT_ANNULE:
                continue
            vues.setdefault(int(reservation["salleId"]), reservation.get("nomSalle"))
    except (KeyError, TypeError, ValueError, AttributeError) as exc:
        raise _invalide() from exc
    return list(vues.items())


def lire_capacite(corps: Any) -> int:
    try:
        capacite = int(corps["capacite"])
    except (KeyError, TypeError, ValueError) as exc:
        raise _invalide() from exc
    if capacite <= 0:
        raise _invalide()
    return capacite


class SalleClient:
    def __init__(self, base_url: str, timeout: float = 5.0, token: str | None = None) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout
        self._token = token

    def with_token(self, token: str | None) -> SalleClient:
        """Copie liée au jeton JWT de l'appelant (retransmis si le service Salles l'exige un jour)."""
        return SalleClient(self._base_url, self._timeout, token)

    def _get(self, chemin: str) -> Any | None:
        return get_json(
            f"{self._base_url}{chemin}",
            timeout=self._timeout,
            token=self._token,
            service="Salles",
            code_indisponible="SERVICE_SALLES_INDISPONIBLE",
            code_invalide="REPONSE_SALLES_INVALIDE",
        )

    def capacite_evenement(self, evenement_id: int) -> CapaciteEvenement:
        reservations = self._get(f"/api/reservations/evenement/{int(evenement_id)}")
        if reservations is None:  # 404 : aucune réservation connue
            return CapaciteEvenement()
        salles: list[SalleReservee] = []
        for salle_id, nom in lire_salles_reservees(reservations):
            corps = self._get(f"/api/salles/{salle_id}")
            if corps is None:
                logger.warning("Salle %s réservée pour l'événement %s mais absente du service Salles.", salle_id, evenement_id)
                continue
            salles.append(SalleReservee(id=salle_id, nom=nom or corps.get("nom"), capacite=lire_capacite(corps)))
        return CapaciteEvenement(salles=tuple(salles))
