"""Signale clairement pourquoi les tests d'intégration ne tournent pas (ou vérifie la connexion)."""

import os

import pytest

from app.tests.integration.conftest import modules_manquants


def test_prerequis_des_tests_dintegration():
    manquants = modules_manquants()
    if manquants:
        pytest.skip(
            "Tests d'intégration NON exécutés : modules absents : "
            + ", ".join(manquants)
            + " (pip install -r requirements.txt)."
        )
    if not os.environ.get("TEST_DATABASE_URL", "").strip():
        pytest.skip(
            "Tests d'intégration NON exécutés : TEST_DATABASE_URL n'est pas défini "
            "(voir .env.example et docker-compose.yml, profil « test »)."
        )

    from sqlalchemy import create_engine, text

    from app.core.config import normaliser_url_base

    moteur = create_engine(normaliser_url_base(os.environ["TEST_DATABASE_URL"]))
    try:
        with moteur.connect() as connexion:
            assert connexion.execute(text("SELECT 1")).scalar_one() == 1
    finally:
        moteur.dispose()
