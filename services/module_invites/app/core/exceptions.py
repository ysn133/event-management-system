"""Exceptions métier. Elles portent un code HTTP et un code d'erreur stable.

"""

from __future__ import annotations

from typing import Any


class AppError(Exception):
    """Erreur applicative de base."""

    status_code: int = 500

    def __init__(self, code: str, message: str, details: list[dict[str, Any]] | None = None) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details


class BadRequestError(AppError):
    """Requête syntaxiquement valide mais inutilisable (ex. aucun critère de recherche)."""

    status_code = 400


class AuthenticationRequiredError(AppError):
    """Identité absente, invalide ou non configurée."""

    status_code = 401


class ForbiddenError(AppError):
    """Utilisateur identifié mais non autorisé."""

    status_code = 403


class NotFoundError(AppError):
    status_code = 404


class ConflictError(AppError):
    status_code = 409


class BusinessValidationError(AppError):
    """Règle métier non respectée (ex. contact manquant pour le canal choisi)."""

    status_code = 422


class NotificationFailedError(AppError):
    """Le fournisseur d'envoi a échoué ou n'a pas confirmé l'envoi."""

    status_code = 502


class NotificationNotConfiguredError(AppError):
    """Aucun fournisseur d'envoi n'est configuré."""

    status_code = 503


class UpstreamError(AppError):
    """Un autre service de l'équipe a répondu de façon inexploitable."""

    status_code = 502


class ServiceUnavailableError(AppError):
    """Un autre service de l'équipe est injoignable (arrêté, réseau, délai dépassé)."""

    status_code = 503
