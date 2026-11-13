"""Fixtures d'intégration : PostgreSQL réel, schéma de l'équipe, application FastAPI réelle.
"""

import importlib.util
import os
from pathlib import Path

import pytest

RACINE = Path(__file__).resolve().parents[3]
SCHEMA_COMMUN = RACINE / "app" / "tests" / "fixtures" / "schema_commun_eventflow.sql"
MODULES_REQUIS = ("sqlalchemy", "fastapi", "psycopg", "httpx")
FICHIERS_INTEGRATION = [
    "test_invites_api.py",
    "test_associations_api.py",
    "test_invitations_api.py",
    "test_statistiques_api.py",
    "test_database_errors.py",
    "test_schema_commun.py",
]


def _present(nom: str) -> bool:
    try:
        return importlib.util.find_spec(nom) is not None
    except ModuleNotFoundError:  # le paquet parent est absent
        return False


def modules_manquants() -> list[str]:
    return [nom for nom in MODULES_REQUIS if not _present(nom)]


TEST_DATABASE_URL = os.environ.get("TEST_DATABASE_URL", "").strip()

if modules_manquants() or not TEST_DATABASE_URL:
    collect_ignore = FICHIERS_INTEGRATION


def _url_sqlalchemy(url: str) -> str:
    from app.core.config import normaliser_url_base

    return normaliser_url_base(url)


@pytest.fixture(scope="session")
def test_database_url() -> str:
    manquants = modules_manquants()
    if manquants:
        pytest.skip(
            "Tests d'intégration NON exécutés : modules absents : "
            + ", ".join(manquants)
            + " (pip install -r requirements.txt)."
        )
    if not TEST_DATABASE_URL:
        pytest.skip(
            "Tests d'intégration NON exécutés : TEST_DATABASE_URL n'est pas défini "
            "(voir .env.example et docker-compose.yml, service « db_test »)."
        )
    nom_base = TEST_DATABASE_URL.split("?")[0].rsplit("/", 1)[-1]
    if "test" not in nom_base.lower():
        pytest.exit(
            f"Refus de lancer les tests : le nom de la base ({nom_base!r}) doit contenir « test » "
            "car ses tables sont supprimées et vidées.",
            returncode=2,
        )
    return _url_sqlalchemy(TEST_DATABASE_URL)


@pytest.fixture(scope="session")
def engine(test_database_url):
    """Repart d'une base propre avec le schéma de l'équipe (copie de ``001_schema.sql``)."""
    from sqlalchemy import create_engine, text

    moteur = create_engine(test_database_url, pool_pre_ping=True)
    with moteur.begin() as connexion:
        # Base de test dédiée (garde-fou sur le nom plus haut) : on repart de zéro.
        connexion.execute(text("DROP SCHEMA IF EXISTS public CASCADE"))
        connexion.execute(text("CREATE SCHEMA public"))
        # Tables de TOUS les modules, telles que définies par l'équipe.
        connexion.exec_driver_sql(SCHEMA_COMMUN.read_text(encoding="utf-8"))
    yield moteur
    moteur.dispose()


@pytest.fixture(autouse=True)
def _base_vide(engine):
    from sqlalchemy import text

    with engine.begin() as connexion:
        connexion.execute(
            text("TRUNCATE invitations, invites, evenements, organisateurs RESTART IDENTITY CASCADE")
        )


@pytest.fixture
def session(engine):
    from sqlalchemy.orm import Session

    with Session(engine) as session_test:
        yield session_test


class EvenementsSimules:
    """Joue le rôle du service Événements en lisant la table ``evenements`` de la base de test."""

    def __init__(self, engine) -> None:
        self._engine = engine
        self.indisponible = False

    def get(self, evenement_id: int):
        from sqlalchemy import text

        from app.clients.evenement_client import EvenementInfo
        from app.core.exceptions import ServiceUnavailableError

        if self.indisponible:
            raise ServiceUnavailableError("SERVICE_EVENEMENTS_INDISPONIBLE", "Le service Événements est injoignable.")
        with self._engine.connect() as connexion:
            ligne = connexion.execute(
                text(
                    "SELECT id, organisateur_id, titre, description, date_debut, date_fin, lieu, statut "
                    "FROM evenements WHERE id = :id"
                ),
                {"id": evenement_id},
            ).first()
        return None if ligne is None else EvenementInfo(*ligne)


@pytest.fixture
def evenements(engine):
    return EvenementsSimules(engine)


@pytest.fixture
def sender():
    from app.services.notification_service import SimulatedNotificationSender

    return SimulatedNotificationSender()


@pytest.fixture
def application(test_database_url, sender, evenements):
    from app.core.config import Settings
    from app.main import create_app

    reglages = Settings(
        app_env="test",
        debug=False,
        database_url=test_database_url,
        cors_origins=(),
        auth_enabled=False,
        organiser_check_enabled=False,
        notification_backend="simulated",
    )
    app = create_app(settings=reglages, sender=sender, evenements=evenements)
    yield app
    app.state.engine.dispose()


@pytest.fixture
def client(application):
    from fastapi.testclient import TestClient

    # raise_server_exceptions=False : on vérifie la réponse 500 réellement renvoyée au client.
    with TestClient(application, raise_server_exceptions=False) as client_test:
        yield client_test


@pytest.fixture
def creer_evenement(engine):
    from sqlalchemy import text

    def _creer(titre: str = "Conférence annuelle", organisateur_id: int = 1) -> int:
        with engine.begin() as connexion:
            # evenements.organisateur_id référence organisateurs (table d'un autre module).
            connexion.execute(
                text(
                    "INSERT INTO organisateurs (id, nom, email, mot_de_passe_hash) "
                    "VALUES (:org, 'Organisateur de test', 'org-' || :org || '@test.local', 'non-utilise') "
                    "ON CONFLICT (id) DO NOTHING"
                ),
                {"org": organisateur_id},
            )
            return connexion.execute(
                text(
                    "INSERT INTO evenements (organisateur_id, titre, date_debut, date_fin, lieu) "
                    "VALUES (:org, :titre, now() + interval '7 days', now() + interval '7 days 2 hours', 'Salle A') "
                    "RETURNING id"
                ),
                {"org": organisateur_id, "titre": titre},
            ).scalar_one()

    return _creer


@pytest.fixture
def creer_invite(engine):
    from sqlalchemy import text

    def _creer(
        nom: str = "Amine Tagarte",
        email: str | None = "amine@example.com",
        telephone: str | None = "0612345678",
    ) -> int:
        with engine.begin() as connexion:
            return connexion.execute(
                text("INSERT INTO invites (nom, email, telephone) VALUES (:n, :e, :t) RETURNING id"),
                {"n": nom, "e": email, "t": telephone},
            ).scalar_one()

    return _creer


@pytest.fixture
def associer(client):
    """Associe via l'API et retourne l'identifiant de l'invitation créée."""

    def _associer(evenement_id: int, invite_id: int) -> int:
        reponse = client.post(f"/api/v1/evenements/{evenement_id}/invites/{invite_id}")
        assert reponse.status_code == 201, reponse.text
        return reponse.json()["id"]

    return _associer
