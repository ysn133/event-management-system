"""Point d'entrée FastAPI du module « Gestion des invités et des invitations ».

Lancer :  uvicorn app.main:app --reload --port 5003
Documentation Swagger : http://localhost:5003/docs
"""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.dependencies import require_user
from app.api.error_handlers import register_error_handlers
from app.api.routes import associations, invitations, invites, statistiques
from app.clients.evenement_client import EvenementClient
from app.core.config import Settings, get_settings
from app.database.connection import create_db_engine, create_session_factory
from app.services.notification_service import NotificationSender, construire_sender

logger = logging.getLogger("app")

API_PREFIX = "/api/v1"
VERSION = "1.0.0"


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    yield
    app.state.engine.dispose()


def create_app(
    settings: Settings | None = None,
    sender: NotificationSender | None = None,
    evenements: EvenementClient | None = None,
) -> FastAPI:
    """Fabrique l'application. Paramètres optionnels pour les tests et l'intégration."""
    settings = settings or get_settings()
    logger.setLevel(logging.DEBUG if settings.debug else logging.INFO)

    engine = create_db_engine(settings.database_url)
    app = FastAPI(
        title="Module Invités et invitations",
        version=VERSION,
        description=(
            "Gestion des invités, association aux événements, invitations et suivi des réponses. "
            "Toutes les erreurs ont le format `{\"error\": {\"code\", \"message\", \"details\"?}}`."
        ),
        lifespan=lifespan,
    )
    app.state.settings = settings
    app.state.engine = engine
    app.state.session_factory = create_session_factory(engine)
    app.state.sender = sender or construire_sender(settings.notification_backend)
    app.state.evenements = evenements or EvenementClient(
        settings.evenements_service_url, settings.evenements_timeout
    )

    if settings.cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=list(settings.cors_origins),
            allow_credentials=settings.cors_allow_credentials,
            allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
            allow_headers=["*"],
        )

    register_error_handlers(app)

    # Toutes les routes métier passent par ``require_user`` (voir app/core/security.py).
    protegee = [Depends(require_user)]
    app.include_router(invites.router, prefix=API_PREFIX, dependencies=protegee)
    app.include_router(associations.router, prefix=API_PREFIX, dependencies=protegee)
    app.include_router(invitations.router, prefix=API_PREFIX, dependencies=protegee)
    app.include_router(statistiques.router, prefix=API_PREFIX, dependencies=protegee)

    @app.get("/health", tags=["Santé"], summary="Vérifie que l'application répond")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    if not settings.auth_enabled:
        logger.warning(
            "AUTH_ENABLED=false : AUCUNE authentification n'est appliquée (développement local uniquement)."
        )
    if settings.notification_backend == "none":
        logger.info("NOTIFICATION_BACKEND=none : l'envoi d'invitations répondra 503 (aucun fournisseur).")
    elif settings.notification_backend == "simulated":
        logger.warning("NOTIFICATION_BACKEND=simulated : les envois sont SIMULÉS, aucun message réel n'est envoyé.")

    return app


app = create_app()
