"""Configuration : cohérence des environnements et absence de fuite de secrets."""

import pytest

from app.core.config import ConfigurationError, Settings, normaliser_url_base

URL = "postgresql://utilisateur:motdepasse_secret@localhost:5432/base"


def reglages(**surcharges) -> Settings:
    valeurs = dict(
        app_env="development",
        debug=False,
        database_url=URL,
        cors_origins=(),
        auth_enabled=False,
        organiser_check_enabled=False,
        notification_backend="none",
    )
    valeurs.update(surcharges)
    return Settings(**valeurs)


def test_developpement_sans_authentification_est_permis():
    assert reglages().auth_enabled is False


def test_production_sans_authentification_refuse_de_demarrer():
    with pytest.raises(ConfigurationError, match="AUTH_ENABLED"):
        reglages(app_env="production", auth_enabled=False)


def test_production_avec_authentification_est_valide():
    assert reglages(app_env="production", auth_enabled=True).app_env == "production"


def test_envoi_simule_interdit_en_production():
    with pytest.raises(ConfigurationError, match="simulated"):
        reglages(app_env="production", auth_enabled=True, notification_backend="simulated")


def test_verification_organisateur_necessite_lauthentification():
    with pytest.raises(ConfigurationError, match="ORGANISER_CHECK_ENABLED"):
        reglages(organiser_check_enabled=True, auth_enabled=False)


@pytest.mark.parametrize("nom,valeur", [("app_env", "staging"), ("notification_backend", "smtp"), ("database_url", "")])
def test_valeurs_invalides_refusees(nom, valeur):
    with pytest.raises(ConfigurationError):
        reglages(**{nom: valeur})


def test_url_de_base_jamais_exposee_dans_repr():
    assert "motdepasse_secret" not in repr(reglages())


def test_normalisation_du_pilote_postgresql():
    assert normaliser_url_base("postgresql://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normaliser_url_base("postgres://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normaliser_url_base("postgresql+psycopg://u:p@h/db") == "postgresql+psycopg://u:p@h/db"


def test_cookies_interdits_avec_origine_joker():
    assert reglages(cors_origins=("*",)).cors_allow_credentials is False
    assert reglages(cors_origins=("http://localhost:3000",)).cors_allow_credentials is True


def test_from_env(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", URL)
    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("AUTH_ENABLED", "true")
    monkeypatch.setenv("CORS_ORIGINS", "http://a.example, http://b.example,")
    monkeypatch.setenv("NOTIFICATION_BACKEND", "none")
    monkeypatch.setenv("ORGANISER_CHECK_ENABLED", "false")
    reglage = Settings.from_env()
    assert reglage.app_env == "production"
    assert reglage.cors_origins == ("http://a.example", "http://b.example")
    assert reglage.database_url.startswith("postgresql+psycopg://")


def test_from_env_sans_database_url(monkeypatch, tmp_path):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    # Isole le test du .env réel du développeur (load_dotenv ne cherche pas depuis le dossier courant).
    monkeypatch.setattr("app.core.config.load_dotenv", lambda *args, **kwargs: False)
    with pytest.raises(ConfigurationError, match="DATABASE_URL"):
        Settings.from_env()


def test_booleen_invalide(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", URL)
    monkeypatch.setenv("AUTH_ENABLED", "peut-etre")
    with pytest.raises(ConfigurationError, match="AUTH_ENABLED"):
        Settings.from_env()


def test_url_du_service_evenements_par_defaut():
    assert reglages().evenements_service_url == "http://localhost:5001"


def test_url_du_service_evenements_doit_etre_http():
    with pytest.raises(ConfigurationError, match="EVENEMENTS_SERVICE_URL"):
        reglages(evenements_service_url="localhost:5001")


def test_delai_du_service_evenements_doit_etre_positif():
    with pytest.raises(ConfigurationError, match="EVENEMENTS_TIMEOUT_SECONDS"):
        reglages(evenements_timeout=0)
