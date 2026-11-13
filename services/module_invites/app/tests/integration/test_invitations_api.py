"""Invitations : envoi (simulé), statuts et listes via l'API, sur PostgreSQL réel."""

import pytest

from app.core.exceptions import NotificationNotConfiguredError
from app.services.notification_service import NotConfiguredSender, NotificationSender, SimulatedNotificationSender


def envoyer(client, evenement_id, **corps):
    return client.post(f"/api/v1/evenements/{evenement_id}/invitations", json=corps)


def test_creation_dune_invitation_envoi_confirme(client, creer_invite, creer_evenement, associer, sender):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    invitation_id = associer(evenement_id, invite_id)

    reponse = envoyer(client, evenement_id, invite_id=invite_id)

    assert reponse.status_code == 200
    corps = reponse.json()
    assert corps["invitation"]["id"] == invitation_id
    assert corps["invitation"]["date_envoi"] is not None
    assert corps["invitation"]["etat_envoi"] == "ENVOYEE"
    assert corps["invitation"]["statut"] == "EN_ATTENTE"  # la réponse de l'invité reste distincte
    assert corps["envoi"] == {"confirme": True, "canal": "EMAIL", "fournisseur": "simulation", "simulation": True}
    assert len(sender.envois) == 1


def test_la_date_envoi_est_persistee(client, creer_invite, creer_evenement, associer):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    invitation_id = associer(evenement_id, invite_id)
    envoyer(client, evenement_id, invite_id=invite_id)
    lue = client.get(f"/api/v1/invitations/{invitation_id}").json()
    assert lue["date_envoi"] is not None and lue["etat_envoi"] == "ENVOYEE"


def test_envoi_sans_association_prealable(client, creer_invite, creer_evenement, sender):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    reponse = envoyer(client, evenement_id, invite_id=invite_id)
    assert reponse.status_code == 409
    assert reponse.json()["error"]["code"] == "ASSOCIATION_INTROUVABLE"
    assert sender.envois == []
    assert client.get(f"/api/v1/evenements/{evenement_id}/invites").json()["total"] == 0


def test_envoi_deja_envoye(client, creer_invite, creer_evenement, associer, sender):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    associer(evenement_id, invite_id)
    assert envoyer(client, evenement_id, invite_id=invite_id).status_code == 200
    reponse = envoyer(client, evenement_id, invite_id=invite_id)
    assert reponse.status_code == 409
    assert reponse.json()["error"]["code"] == "INVITATION_DEJA_ENVOYEE"
    assert len(sender.envois) == 1


def test_envoi_evenement_ou_invite_inexistant(client, creer_invite, creer_evenement):
    assert envoyer(client, 999, invite_id=creer_invite()).status_code == 404
    assert envoyer(client, creer_evenement(), invite_id=999).status_code == 404


def test_envoi_sms_sans_telephone(client, creer_invite, creer_evenement, associer):
    invite_id = creer_invite(telephone=None)
    evenement_id = creer_evenement()
    associer(evenement_id, invite_id)
    reponse = envoyer(client, evenement_id, invite_id=invite_id, canal="SMS")
    assert reponse.status_code == 422
    assert reponse.json()["error"]["code"] == "CONTACT_MANQUANT"


def test_echec_du_fournisseur_502_sans_date_envoi(client, application, creer_invite, creer_evenement, associer):
    application.state.sender = SimulatedNotificationSender(echec=True)
    invite_id, evenement_id = creer_invite(), creer_evenement()
    invitation_id = associer(evenement_id, invite_id)
    reponse = envoyer(client, evenement_id, invite_id=invite_id)
    assert reponse.status_code == 502
    assert client.get(f"/api/v1/invitations/{invitation_id}").json()["date_envoi"] is None


def test_exception_du_fournisseur_502_sans_fuite(client, application, creer_invite, creer_evenement, associer):
    class Casse(NotificationSender):
        def envoyer(self, canal, destinataire, message):
            raise RuntimeError("secret_smtp_123")

    application.state.sender = Casse()
    invite_id, evenement_id = creer_invite(), creer_evenement()
    associer(evenement_id, invite_id)
    reponse = envoyer(client, evenement_id, invite_id=invite_id)
    assert reponse.status_code == 502
    assert "secret_smtp_123" not in reponse.text


def test_aucun_fournisseur_configure_503(client, application, creer_invite, creer_evenement, associer):
    application.state.sender = NotConfiguredSender()
    invite_id, evenement_id = creer_invite(), creer_evenement()
    invitation_id = associer(evenement_id, invite_id)
    reponse = envoyer(client, evenement_id, invite_id=invite_id)
    assert reponse.status_code == 503
    assert reponse.json()["error"]["code"] == "FOURNISSEUR_ENVOI_NON_CONFIGURE"
    assert client.get(f"/api/v1/invitations/{invitation_id}").json()["date_envoi"] is None


def test_consultation_dune_invitation(client, creer_invite, creer_evenement, associer):
    invite_id, evenement_id = creer_invite(nom="Sara"), creer_evenement()
    invitation_id = associer(evenement_id, invite_id)
    reponse = client.get(f"/api/v1/invitations/{invitation_id}")
    assert reponse.status_code == 200
    assert reponse.json()["invite"]["nom"] == "Sara"


def test_consultation_dune_invitation_inexistante(client):
    reponse = client.get("/api/v1/invitations/999")
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "INVITATION_INTROUVABLE"


@pytest.mark.parametrize("statut", ["ACCEPTEE", "REFUSEE", "EN_ATTENTE"])
def test_modification_du_statut(client, creer_invite, creer_evenement, associer, statut):
    invitation_id = associer(creer_evenement(), creer_invite())
    reponse = client.patch(f"/api/v1/invitations/{invitation_id}/statut", json={"statut": statut})
    assert reponse.status_code == 200
    assert reponse.json()["statut"] == statut
    assert client.get(f"/api/v1/invitations/{invitation_id}").json()["statut"] == statut


@pytest.mark.parametrize("statut", ["PEUT_ETRE", "accepte", "", "ENVOYEE", None])
def test_rejet_dun_statut_invalide(client, creer_invite, creer_evenement, associer, statut):
    invitation_id = associer(creer_evenement(), creer_invite())
    reponse = client.patch(f"/api/v1/invitations/{invitation_id}/statut", json={"statut": statut})
    assert reponse.status_code == 422
    assert client.get(f"/api/v1/invitations/{invitation_id}").json()["statut"] == "EN_ATTENTE"


def test_statut_invalide_refuse_par_la_base_apres_lamelioration_recommandee(
    engine, creer_invite, creer_evenement, associer
):
    """La base commune n'a PAS de CHECK sur statut : sql/ameliorations_recommandees.sql l'ajoute.

    Le test applique ce script, vérifie qu'un accès direct avec un statut invalide est refusé, puis
    retire la contrainte (la base de test est partagée entre les tests de la session).
    """
    from pathlib import Path

    from sqlalchemy import text
    from sqlalchemy.exc import IntegrityError

    racine = Path(__file__).resolve().parents[3]
    script = (racine / "sql" / "ameliorations_recommandees.sql").read_text(encoding="utf-8")
    script = "\n".join(l for l in script.splitlines() if l.strip() not in {"BEGIN;", "COMMIT;"})
    # psycopg lit « % » comme un paramètre : on l'échappe (le script contient « % » dans un RAISE).
    script = script.replace("%", "%%")

    invitation_id = associer(creer_evenement(), creer_invite())
    with engine.begin() as connexion:
        connexion.exec_driver_sql(script)
    try:
        with pytest.raises(IntegrityError), engine.begin() as connexion:
            connexion.execute(text("UPDATE invitations SET statut = 'PEUT_ETRE' WHERE id = :i"), {"i": invitation_id})
    finally:
        with engine.begin() as connexion:
            connexion.exec_driver_sql("ALTER TABLE invitations DROP CONSTRAINT IF EXISTS ck_invitations_statut")


def test_modification_du_statut_dune_invitation_inexistante(client):
    assert client.patch("/api/v1/invitations/999/statut", json={"statut": "ACCEPTEE"}).status_code == 404


def test_le_statut_nenvoie_rien(client, creer_invite, creer_evenement, associer, sender):
    invitation_id = associer(creer_evenement(), creer_invite())
    client.patch(f"/api/v1/invitations/{invitation_id}/statut", json={"statut": "ACCEPTEE"})
    assert sender.envois == []
    assert client.get(f"/api/v1/invitations/{invitation_id}").json()["date_envoi"] is None


def test_liste_des_invitations_dun_evenement_et_filtres(client, creer_invite, creer_evenement, associer):
    evenement_id = creer_evenement()
    a, b, c = creer_invite(nom="A"), creer_invite(nom="B", email="b@example.com"), creer_invite(nom="C", email="c@example.com")
    associer(evenement_id, a)
    invitation_b = associer(evenement_id, b)
    associer(evenement_id, c)
    client.patch(f"/api/v1/invitations/{invitation_b}/statut", json={"statut": "ACCEPTEE"})
    envoyer(client, evenement_id, invite_id=a)

    base = f"/api/v1/evenements/{evenement_id}/invitations"
    tous = client.get(base).json()
    assert tous["total"] == 3 and [i["invite"]["nom"] for i in tous["items"]] == ["A", "B", "C"]
    assert [i["invite"]["nom"] for i in client.get(base, params={"statut": "ACCEPTEE"}).json()["items"]] == ["B"]
    assert [i["invite"]["nom"] for i in client.get(base, params={"envoyee": "true"}).json()["items"]] == ["A"]
    assert client.get(base, params={"envoyee": "false"}).json()["total"] == 2
    assert client.get(base, params={"limit": 1, "offset": 1}).json()["items"][0]["invite"]["nom"] == "B"


def test_liste_des_invitations_dun_evenement_inexistant(client):
    assert client.get("/api/v1/evenements/999/invitations").status_code == 404


def test_liste_des_invitations_dun_invite(client, creer_invite, creer_evenement, associer):
    invite_id = creer_invite()
    e1, e2 = creer_evenement("E1"), creer_evenement("E2")
    associer(e1, invite_id)
    associer(e2, invite_id)
    reponse = client.get(f"/api/v1/invites/{invite_id}/invitations")
    assert reponse.status_code == 200
    assert [i["evenement_id"] for i in reponse.json()["items"]] == [e1, e2]


def test_liste_des_invitations_dun_invite_inexistant(client):
    assert client.get("/api/v1/invites/999/invitations").status_code == 404


def test_routes_protegees_quand_lauthentification_est_activee_sans_fournisseur(
    test_database_url, creer_invite
):
    """AUTH_ENABLED=true sans fournisseur d'identité : accès refusé, jamais accordé."""
    from fastapi.testclient import TestClient

    from app.core.config import Settings
    from app.core.security import set_identity_provider
    from app.main import create_app

    set_identity_provider(None)
    reglages = Settings(
        app_env="test",
        debug=False,
        database_url=test_database_url,
        cors_origins=(),
        auth_enabled=True,
        organiser_check_enabled=False,
        notification_backend="none",
    )
    app = create_app(settings=reglages)
    with TestClient(app, raise_server_exceptions=False) as client_auth:
        reponse = client_auth.get("/api/v1/invites")
        assert reponse.status_code == 401
        assert reponse.json()["error"]["code"] == "AUTHENTIFICATION_NON_CONFIGUREE"
        assert client_auth.get("/health").status_code == 200  # la santé reste publique
    app.state.engine.dispose()


def test_exception_de_service_non_configure_est_bien_une_erreur_applicative():
    assert NotificationNotConfiguredError.status_code == 503