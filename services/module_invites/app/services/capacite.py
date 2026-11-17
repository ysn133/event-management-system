"""Règle « les invités ne dépassent pas la capacité des salles réservées ».

Ce qui occupe une place : toute invitation dont la réponse n'est PAS ``REFUSEE`` (en attente ou acceptée).
Un invité qui refuse libère sa place. Sans salle réservée, la capacité est inconnue : aucune limite.
"""

from __future__ import annotations

from app.clients.salle_client import CapaciteEvenement
from app.core.exceptions import ConflictError


def places_occupees(compteurs: dict[str, int]) -> int:
    """Places occupées d'après ``InvitationRepository.compter_par_statut``."""
    return compteurs["en_attente"] + compteurs["acceptees"]


def verifier_capacite(capacite: CapaciteEvenement, occupees: int, supplementaires: int = 1) -> None:
    """Lève ``409 CAPACITE_SALLE_DEPASSEE`` si ``occupees + supplementaires`` dépasse la capacité."""
    total = capacite.total
    if total is None:
        return
    if occupees + supplementaires > total:
        raise ConflictError(
            "CAPACITE_SALLE_DEPASSEE",
            f"La capacité des salles réservées ({total} place(s)) serait dépassée : "
            f"{occupees} invité(s) occupent déjà une place.",
        )
