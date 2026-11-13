"""Gestion centralisée des erreurs (nécessite FastAPI ; ignoré si FastAPI n'est pas installé)."""

import pytest

pytest.importorskip("fastapi")
pytest.importorskip("sqlalchemy")
pytest.importorskip("httpx")

from fastapi import FastAPI  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from pydantic import BaseModel  # noqa: E402
from sqlalchemy.exc import IntegrityError, OperationalError  # noqa: E402

from app.api.error_handlers import register_error_handlers  # noqa: E402
from app.core.exceptions import ConflictError, NotFoundError  # noqa: E402


class Corps(BaseModel):
    nom: str


def fabriquer_client() -> TestClient:
    app = FastAPI()
    register_error_handlers(app)

    @app.get("/introuvable")
    def introuvable():
        raise NotFoundError("INVITE_INTROUVABLE", "Invité 1 introuvable.")

    @app.get("/conflit")
    def conflit():
        raise ConflictError("ASSOCIATION_EXISTANTE", "doublon")

    @app.get("/base-indisponible")
    def base_indisponible():
        raise OperationalError(
            "SELECT 1", {}, Exception('connexion refusée postgresql://u:motdepasse_secret@h/db')
        )

    @app.get("/integrite")
    def integrite():
        raise IntegrityError("INSERT ...", {"nom": "valeur_sensible"}, Exception("violation"))

    @app.get("/bug")
    def bug():
        raise RuntimeError("trace interne secrète")

    @app.post("/valider")
    def valider(corps: Corps):
        return corps

    return TestClient(app, raise_server_exceptions=False)


def test_erreur_metier_format_unique():
    reponse = fabriquer_client().get("/introuvable")
    assert reponse.status_code == 404
    assert reponse.json() == {"error": {"code": "INVITE_INTROUVABLE", "message": "Invité 1 introuvable."}}


def test_conflit_409():
    assert fabriquer_client().get("/conflit").status_code == 409


@pytest.mark.parametrize("chemin", ["/base-indisponible", "/integrite", "/bug"])
def test_erreurs_techniques_donnent_500_sans_fuite(chemin):
    reponse = fabriquer_client().get(chemin)
    assert reponse.status_code == 500
    assert reponse.json() == {"error": {"code": "ERREUR_INTERNE", "message": "Erreur interne du serveur."}}
    corps = reponse.text
    for secret in ("motdepasse_secret", "valeur_sensible", "trace interne", "postgresql://"):
        assert secret not in corps


def test_validation_422_sans_reinjecter_la_valeur_saisie():
    reponse = fabriquer_client().post("/valider", json={"nom": 12345678})
    assert reponse.status_code == 422
    corps = reponse.json()["error"]
    assert corps["code"] == "DONNEES_INVALIDES"
    assert corps["details"][0]["champ"] == "nom"
    assert "12345678" not in reponse.text


def test_route_inconnue_utilise_le_meme_format():
    reponse = fabriquer_client().get("/nexiste-pas")
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "ROUTE_INTROUVABLE"
