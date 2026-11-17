"""Point d'intégration de l'authentification et de l'autorisation.

IMPORTANT : ce module NE FOURNIT PAS de système d'identité. Il définit seulement l'endroit où
brancher celui du projet principal.

Comportement :
- ``AUTH_ENABLED=false`` : réservé aux tests ; refusé au démarrage hors ``APP_ENV=test``.
- ``AUTH_ENABLED=true`` sans fournisseur d'identité branché : accès REFUSÉ (401), jamais accordé.
- ``AUTH_ENABLED=true`` avec un fournisseur : l'identité qu'il retourne est utilisée.
- ``ORGANISER_CHECK_ENABLED=true`` : seul l'organisateur de l'événement peut agir dessus.

Pour brancher l'authentification du projet principal, voir la section « Intégration » du README.
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
    fournisseur = getattr(getattr(getattr(request, "app", None), "state", None), "identity_provider", None) or _provider
    if fournisseur is None:
        raise AuthenticationRequiredError(
            "AUTHENTIFICATION_NON_CONFIGUREE",
            "L'authentification est activée mais aucun fournisseur d'identité n'est branché : accès refusé.",
        )
    user = fournisseur.authenticate(request)
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
