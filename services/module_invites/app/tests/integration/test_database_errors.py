"""Gestion des erreurs de base de données, sur PostgreSQL réel."""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.exceptions import ConflictError, NotFoundError
from app.main import create_app
from app.repositories.invitation_repository import InvitationRepository
from app.repositories.invite_repository import InviteRepository


def test_base_injoignable_donne_500_generique_sans_fuite():
    reglages = Settings(
        app_env="test",
        debug=False,
        database_url="postgresql+psycopg://utilisateur:motdepasse_secret@127.0.0.1:1/base_test",
        cors_origins=(),
        auth_enabled=False,
        organiser_check_enabled=False,
        notification_backend="none",
    )
    app = create_app(settings=reglages)
    with TestClient(app, raise_server_exceptions=False) as client:
        reponse = client.get("/api/v1/invites")
    app.state.engine.dispose()

    assert reponse.status_code == 500
    assert reponse.json() == {"error": {"code": "ERREUR_INTERNE", "message": "Erreur interne du serveur."}}
    for secret in ("motdepasse_secret", "utilisateur", "127.0.0.1", "base_test", "postgresql"):
        assert secret not in reponse.text


def test_le_depot_traduit_la_violation_dunicite_en_409(session: Session, creer_invite, creer_evenement):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    depot = InvitationRepository(session)
    depot.add(invite_id, evenement_id)
    session.commit()
    with pytest.raises(ConflictError) as exc:
        depot.add(invite_id, evenement_id)  # contrôle applicatif contourné : la base tranche
    assert exc.value.code == "ASSOCIATION_EXISTANTE"
    session.rollback()


def test_le_depot_traduit_la_violation_de_cle_etrangere_invite_en_404(session: Session, creer_evenement):
    depot = InvitationRepository(session)
    with pytest.raises(NotFoundError) as exc:
        depot.add(999, creer_evenement())
    assert exc.value.code == "INVITE_INTROUVABLE"
    session.rollback()


def test_le_depot_traduit_la_violation_de_cle_etrangere_evenement_en_404(session: Session, creer_invite):
    depot = InvitationRepository(session)
    with pytest.raises(NotFoundError) as exc:
        depot.add(creer_invite(), 999)
    assert exc.value.code == "EVENEMENT_INTROUVABLE"
    session.rollback()


def test_la_suppression_dun_invite_lie_est_traduite_en_409_par_le_depot(
    session: Session, creer_invite, creer_evenement
):
    """Course simulée : le contrôle du service est contourné, la clé étrangère protège."""
    invite_id, evenement_id = creer_invite(), creer_evenement()
    InvitationRepository(session).add(invite_id, evenement_id)
    session.commit()
    depot = InviteRepository(session)
    invite = depot.get(invite_id)
    with pytest.raises(ConflictError) as exc:
        depot.delete(invite)
    assert exc.value.code == "INVITE_AVEC_INVITATIONS"
    session.rollback()


def test_une_erreur_en_cours_de_transaction_annule_tout(client, creer_invite, creer_evenement, application):
    """Si l'enregistrement échoue, rien n'est persisté (rollback du service)."""
    invite_id, evenement_id = creer_invite(), creer_evenement()
    assert client.post(f"/api/v1/evenements/{evenement_id}/invites/{invite_id}").status_code == 201
    assert client.post(f"/api/v1/evenements/{evenement_id}/invites/{invite_id}").status_code == 409
    assert client.get(f"/api/v1/evenements/{evenement_id}/invites").json()["total"] == 1
