"""Gestion centralisée des erreurs : toutes les réponses d'erreur ont le même format JSON.

"""

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.exceptions import AppError

logger = logging.getLogger(__name__)

MESSAGE_ERREUR_INTERNE = "Erreur interne du serveur."


def _reponse(
    status_code: int, code: str, message: str, details: list[dict[str, Any]] | None = None
) -> JSONResponse:
    corps: dict[str, Any] = {"error": {"code": code, "message": message}}
    if details:
        corps["error"]["details"] = details
    return JSONResponse(status_code=status_code, content=corps)


async def gerer_erreur_applicative(request: Request, exc: AppError) -> JSONResponse:
    return _reponse(exc.status_code, exc.code, exc.message, exc.details)


async def gerer_erreur_validation(request: Request, exc: RequestValidationError) -> JSONResponse:
    """422 : liste des champs fautifs, sans renvoyer les valeurs saisies."""
    details = []
    for erreur in exc.errors():
        emplacement = [str(partie) for partie in erreur["loc"]]
        source = emplacement[0] if emplacement else ""
        champ = ".".join(emplacement[1:]) if len(emplacement) > 1 else ""
        details.append(
            {
                "source": source,
                "champ": champ,
                "message": str(erreur["msg"]).removeprefix("Value error, "),
                "type": erreur["type"],
            }
        )
    return _reponse(422, "DONNEES_INVALIDES", "Les données envoyées sont invalides.", details)


async def gerer_erreur_http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    codes = {404: "ROUTE_INTROUVABLE", 405: "METHODE_NON_AUTORISEE"}
    return _reponse(exc.status_code, codes.get(exc.status_code, "ERREUR_HTTP"), str(exc.detail))


async def gerer_erreur_base_de_donnees(request: Request, exc: SQLAlchemyError) -> JSONResponse:
    logger.exception("Erreur base de données sur %s %s", request.method, request.url.path)
    return _reponse(500, "ERREUR_INTERNE", MESSAGE_ERREUR_INTERNE)


async def gerer_erreur_inattendue(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Erreur inattendue sur %s %s", request.method, request.url.path)
    return _reponse(500, "ERREUR_INTERNE", MESSAGE_ERREUR_INTERNE)


def register_error_handlers(app: FastAPI) -> None:
    app.add_exception_handler(AppError, gerer_erreur_applicative)  # type: ignore[arg-type]
    app.add_exception_handler(RequestValidationError, gerer_erreur_validation)  # type: ignore[arg-type]
    app.add_exception_handler(StarletteHTTPException, gerer_erreur_http)  # type: ignore[arg-type]
    app.add_exception_handler(SQLAlchemyError, gerer_erreur_base_de_donnees)  # type: ignore[arg-type]
    app.add_exception_handler(Exception, gerer_erreur_inattendue)
