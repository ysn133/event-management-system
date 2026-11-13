"""Configuration lue dans l'environnement (python-dotenv). Aucun secret n'est codé en dur."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from functools import lru_cache

from pathlib import Path

from dotenv import load_dotenv

# Seul le .env DU MODULE est lu (à la racine du module), jamais celui d'un dossier parent : le dépôt de
# l'équipe a son propre .env à sa racine, aux variables différentes.
FICHIER_ENV = Path(__file__).resolve().parents[2] / ".env"

ENVIRONNEMENTS = ("development", "test", "production")
BACKENDS_NOTIFICATION = ("none", "simulated")


class ConfigurationError(RuntimeError):
    """Configuration absente ou incohérente."""


def normaliser_url_base(url: str) -> str:
    """Force le pilote psycopg 3 pour les URL PostgreSQL « nues »."""
    for prefixe in ("postgresql://", "postgres://"):
        if url.startswith(prefixe):
            return "postgresql+psycopg://" + url[len(prefixe):]
    return url


def get_database_url() -> str:
    """Retourne DATABASE_URL."""
    load_dotenv(FICHIER_ENV)  # les variables déjà définies dans l'environnement restent prioritaires
    url = os.environ.get("DATABASE_URL", "").strip()
    if not url:
        raise ConfigurationError(
            "La variable d'environnement DATABASE_URL est absente. "
            "Copiez .env.example vers .env et renseignez-la."
        )
    return normaliser_url_base(url)


def _lire_bool(nom: str, defaut: bool) -> bool:
    brut = os.environ.get(nom)
    if brut is None or not brut.strip():
        return defaut
    valeur = brut.strip().lower()
    if valeur in {"1", "true", "yes", "oui", "on"}:
        return True
    if valeur in {"0", "false", "no", "non", "off"}:
        return False
    raise ConfigurationError(f"{nom} doit valoir true ou false.")


def _lire_float(nom: str, defaut: float) -> float:
    brut = os.environ.get(nom)
    if brut is None or not brut.strip():
        return defaut
    try:
        return float(brut)
    except ValueError as exc:
        raise ConfigurationError(f"{nom} doit être un nombre.") from exc


@dataclass(frozen=True)
class Settings:
    """Paramètres de l'application. ``database_url`` est masqué dans ``repr``."""

    app_env: str
    debug: bool
    database_url: str = field(repr=False)
    cors_origins: tuple[str, ...]
    auth_enabled: bool
    organiser_check_enabled: bool
    notification_backend: str
    # Service Événements de l'équipe (voir app/clients/evenement_client.py).
    evenements_service_url: str = "http://localhost:5001"
    evenements_timeout: float = 5.0

    def __post_init__(self) -> None:
        if self.app_env not in ENVIRONNEMENTS:
            raise ConfigurationError(f"APP_ENV doit être l'une des valeurs : {', '.join(ENVIRONNEMENTS)}.")
        if self.notification_backend not in BACKENDS_NOTIFICATION:
            raise ConfigurationError(
                f"NOTIFICATION_BACKEND doit être l'une des valeurs : {', '.join(BACKENDS_NOTIFICATION)}."
            )
        if not self.database_url:
            raise ConfigurationError("DATABASE_URL est vide.")
        if not self.evenements_service_url.startswith(("http://", "https://")):
            raise ConfigurationError("EVENEMENTS_SERVICE_URL doit commencer par http:// ou https://.")
        if self.evenements_timeout <= 0:
            raise ConfigurationError("EVENEMENTS_TIMEOUT_SECONDS doit être positif.")
        if self.app_env == "production" and not self.auth_enabled:
            raise ConfigurationError(
                "En production, AUTH_ENABLED=true est obligatoire : "
                "l'application refuse de démarrer sans authentification."
            )
        if self.app_env == "production" and self.notification_backend == "simulated":
            raise ConfigurationError(
                "NOTIFICATION_BACKEND=simulated est interdit en production : "
                "une simulation ne doit jamais enregistrer d'envoi comme réel."
            )
        if self.organiser_check_enabled and not self.auth_enabled:
            raise ConfigurationError(
                "ORGANISER_CHECK_ENABLED=true nécessite AUTH_ENABLED=true "
                "(on ne peut pas vérifier l'organisateur sans utilisateur identifié)."
            )

    @property
    def cors_allow_credentials(self) -> bool:
        """Les cookies/credentials ne sont jamais autorisés avec l'origine joker."""
        return "*" not in self.cors_origins

    @classmethod
    def from_env(cls) -> Settings:
        database_url = get_database_url()  # charge aussi le fichier .env
        origines = tuple(
            origine.strip()
            for origine in os.environ.get("CORS_ORIGINS", "").split(",")
            if origine.strip()
        )
        return cls(
            app_env=os.environ.get("APP_ENV", "development").strip().lower(),
            debug=_lire_bool("DEBUG", False),
            database_url=database_url,
            cors_origins=origines,
            auth_enabled=_lire_bool("AUTH_ENABLED", False),
            organiser_check_enabled=_lire_bool("ORGANISER_CHECK_ENABLED", False),
            notification_backend=os.environ.get("NOTIFICATION_BACKEND", "none").strip().lower(),
            evenements_service_url=(
                os.environ.get("EVENEMENTS_SERVICE_URL", "").strip() or "http://localhost:5001"
            ),
            evenements_timeout=_lire_float("EVENEMENTS_TIMEOUT_SECONDS", 5.0),
        )


@lru_cache
def get_settings() -> Settings:
    return Settings.from_env()
