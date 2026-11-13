"""Point d'intégration de l'authentification et de l'autorisation.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from app.core.exceptions import AuthenticationRequiredError, ForbiddenError


@dataclass(frozen=True)
class CurrentUser:
    """Utilisateur identifié. ``id`` doit correspondre à ``evenements.organisateur_id``."""

    id: int


class IdentityProvider(Protocol):
    """Contrat à implémenter par le module d'authentification principal."""

    def authenticate(self, request: Any) -> CurrentUser | None:
        """Retourne l'utilisateur de la requête, ou None s'il n'est pas authentifié."""


_provider: IdentityProvider | None = None


def set_identity_provider(provider: IdentityProvider | None) -> None:
    """Branche (ou débranche avec None) le fournisseur d'identité du projet principal."""
    global _provider
    _provider = provider


def get_identity_provider() -> IdentityProvider | None:
    return _provider


def resolve_user(request: Any, auth_enabled: bool) -> CurrentUser | None:
    """Identifie l'appelant. Refuse l'accès si l'authentification est active mais inutilisable."""
    if not auth_enabled:
        return None
    if _provider is None:
        raise AuthenticationRequiredError(
            "AUTHENTIFICATION_NON_CONFIGUREE",
            "L'authentification est activée mais aucun fournisseur d'identité n'est branché : accès refusé.",
        )
    user = _provider.authenticate(request)
    if user is None:
        raise AuthenticationRequiredError("AUTHENTIFICATION_REQUISE", "Authentification requise.")
    return user


@dataclass(frozen=True)
class AccessPolicy:
    """Contrôle « l'utilisateur est l'organisateur de l'événement ». Inactif par défaut."""

    enabled: bool = False
    user: CurrentUser | None = None

    def verifier_evenement(self, evenement: Any) -> None:
        if not self.enabled:
            return
        if self.user is None:
            raise AuthenticationRequiredError("AUTHENTIFICATION_REQUISE", "Authentification requise.")
        if evenement.organisateur_id != self.user.id:
            raise ForbiddenError("ACCES_EVENEMENT_REFUSE", "Vous n'êtes pas l'organisateur de cet événement.")
