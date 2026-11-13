"""Association invité ↔ événement via l'API, sur PostgreSQL réel."""

from concurrent.futures import ThreadPoolExecutor

from fastapi.testclient import TestClient


def url(evenement_id, invite_id):
    return f"/api/v1/evenements/{evenement_id}/invites/{invite_id}"


def test_association_a_un_evenement_existant(client, creer_invite, creer_evenement):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    reponse = client.post(url(evenement_id, invite_id))
    assert reponse.status_code == 201
    corps = reponse.json()
    assert (corps["invite_id"], corps["evenement_id"]) == (invite_id, evenement_id)
    assert corps["statut"] == "EN_ATTENTE"
    assert corps["date_envoi"] is None
    assert corps["etat_envoi"] == "NON_ENVOYEE"


def test_association_a_un_evenement_inexistant(client, creer_invite):
    reponse = client.post(url(999, creer_invite()))
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "EVENEMENT_INTROUVABLE"


def test_association_dun_invite_inexistant(client, creer_evenement):
    reponse = client.post(url(creer_evenement(), 999))
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "INVITE_INTROUVABLE"


def test_detection_des_associations_en_double(client, creer_invite, creer_evenement):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    assert client.post(url(evenement_id, invite_id)).status_code == 201
    reponse = client.post(url(evenement_id, invite_id))
    assert reponse.status_code == 409
    assert reponse.json()["error"]["code"] == "ASSOCIATION_EXISTANTE"


def test_un_invite_participe_a_plusieurs_evenements(client, creer_invite, creer_evenement):
    invite_id = creer_invite()
    e1, e2 = creer_evenement("E1"), creer_evenement("E2")
    assert client.post(url(e1, invite_id)).status_code == 201
    assert client.post(url(e2, invite_id)).status_code == 201
    reponse = client.get(f"/api/v1/invites/{invite_id}/evenements")
    assert reponse.status_code == 200
    assert [e["titre"] for e in reponse.json()["items"]] == ["E1", "E2"]


def test_liste_des_invites_dun_evenement(client, creer_invite, creer_evenement, associer):
    evenement_id = creer_evenement()
    a, b = creer_invite(nom="A"), creer_invite(nom="B", email="b@example.com")
    associer(evenement_id, a)
    invitation_b = associer(evenement_id, b)
    client.patch(f"/api/v1/invitations/{invitation_b}/statut", json={"statut": "ACCEPTEE"})

    reponse = client.get(f"/api/v1/evenements/{evenement_id}/invites")
    assert reponse.status_code == 200
    assert [i["nom"] for i in reponse.json()["items"]] == ["A", "B"]
    assert reponse.json()["items"][1]["statut_invitation"] == "ACCEPTEE"

    filtre = client.get(f"/api/v1/evenements/{evenement_id}/invites", params={"statut": "ACCEPTEE"})
    assert [i["nom"] for i in filtre.json()["items"]] == ["B"]
    assert client.get(f"/api/v1/evenements/{evenement_id}/invites", params={"statut": "PEUT_ETRE"}).status_code == 422


def test_listes_dun_evenement_ou_dun_invite_inexistant(client):
    assert client.get("/api/v1/evenements/999/invites").status_code == 404
    assert client.get("/api/v1/invites/999/evenements").status_code == 404


def test_suppression_de_lassociation(client, creer_invite, creer_evenement, associer):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    associer(evenement_id, invite_id)
    assert client.delete(url(evenement_id, invite_id)).status_code == 204
    assert client.get(f"/api/v1/evenements/{evenement_id}/invites").json()["total"] == 0
    assert client.post(url(evenement_id, invite_id)).status_code == 201  # ré-associable


def test_suppression_dune_association_inexistante(client, creer_invite, creer_evenement):
    reponse = client.delete(url(creer_evenement(), creer_invite()))
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "ASSOCIATION_INTROUVABLE"


def test_suppression_refusee_si_invitation_envoyee(client, creer_invite, creer_evenement, associer):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    associer(evenement_id, invite_id)
    envoi = client.post(f"/api/v1/evenements/{evenement_id}/invitations", json={"invite_id": invite_id})
    assert envoi.status_code == 200
    reponse = client.delete(url(evenement_id, invite_id))
    assert reponse.status_code == 409
    assert reponse.json()["error"]["code"] == "ASSOCIATION_NON_SUPPRIMABLE"


def test_suppression_refusee_si_reponse_enregistree(client, creer_invite, creer_evenement, associer):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    invitation_id = associer(evenement_id, invite_id)
    client.patch(f"/api/v1/invitations/{invitation_id}/statut", json={"statut": "REFUSEE"})
    assert client.delete(url(evenement_id, invite_id)).status_code == 409


def test_associations_simultanees_un_seul_succes(application, creer_invite, creer_evenement, engine):
    """Protection contre les requêtes concurrentes : la contrainte UNIQUE de PostgreSQL tranche."""
    from sqlalchemy import text

    invite_id, evenement_id = creer_invite(), creer_evenement()

    def appeler(_):
        with TestClient(application, raise_server_exceptions=False) as client_thread:
            return client_thread.post(url(evenement_id, invite_id)).status_code

    with ThreadPoolExecutor(max_workers=8) as pool:
        statuts = list(pool.map(appeler, range(8)))

    assert statuts.count(201) == 1, statuts
    assert statuts.count(409) == 7, statuts
    with engine.connect() as connexion:
        nombre = connexion.execute(
            text("SELECT count(*) FROM invitations WHERE invite_id = :i AND evenement_id = :e"),
            {"i": invite_id, "e": evenement_id},
        ).scalar_one()
    assert nombre == 1


def test_service_evenements_indisponible_renvoie_503(client, creer_invite, evenements):
    invite_id = creer_invite()
    evenements.indisponible = True
    reponse = client.post(url(1, invite_id))
    assert reponse.status_code == 503
    assert reponse.json()["error"]["code"] == "SERVICE_EVENEMENTS_INDISPONIBLE"


def test_statistiques_service_evenements_indisponible_renvoie_503(client, creer_evenement, evenements):
    evenement_id = creer_evenement()
    evenements.indisponible = True
    assert client.get(f"/api/v1/evenements/{evenement_id}/statistiques-invitations").status_code == 503
