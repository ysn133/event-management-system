"""Configuration commune des tests.

- Force un environnement de test AVANT l'import de l'application (jamais la vraie base).
- Applique automatiquement les marqueurs ``unit`` / ``integration`` selon le dossier.
"""

import os
from pathlib import Path

from dotenv import load_dotenv

# Permet de ne définir TEST_DATABASE_URL que dans .env (les variables déjà définies dans le shell
# restent prioritaires). Les réglages sensibles ci-dessous sont ensuite FORCÉS pour les tests.
load_dotenv(Path(__file__).resolve().parents[2] / ".env")

# La base utilisée par les tests est TOUJOURS TEST_DATABASE_URL, jamais DATABASE_URL du .env.
os.environ["APP_ENV"] = "test"
os.environ["AUTH_ENABLED"] = "false"
os.environ["ORGANISER_CHECK_ENABLED"] = "false"
os.environ["NOTIFICATION_BACKEND"] = "simulated"
os.environ["DATABASE_URL"] = os.environ.get(
    "TEST_DATABASE_URL", "postgresql://test:test@localhost:5433/invites_test"
)


def pytest_collection_modifyitems(config, items):
    import pytest

    for item in items:
        chemin = Path(str(item.fspath)).parts
        if "unit" in chemin:
            item.add_marker(pytest.mark.unit)
        elif "integration" in chemin:
            item.add_marker(pytest.mark.integration)
