"""Statistiques des invitations via l'API, sur PostgreSQL réel."""

import pytest


def stats(client, evenement_id):
    return client.get(f"/api/v1/evenements/{evenement_id}/statistiques-invitations")


def test_calcul_des_statistiques(client, creer_invite, creer_evenement, associer):
    evenement_id, autre_evenement = creer_evenement("Cible"), creer_evenement("Autre")
    statuts = ["ACCEPTEE"] * 3 + ["REFUSEE"] * 2 + ["EN_ATTENTE"] * 5
    for i, statut in enumerate(statuts):
        invite_id = creer_invite(nom=f"Invité {i}", email=f"i{i}@example.com")
        invitation_id = associer(evenement_id, invite_id)
        client.patch(f"/api/v1/invitations/{invitation_id}/statut", json={"statut": statut})
    # Les invitations d'un autre événement ne doivent pas être comptées.
    hors_perimetre = associer(autre_evenement, creer_invite(nom="Hors", email="hors@example.com"))
    client.patch(f"/api/v1/invitations/{hors_perimetre}/statut", json={"statut": "ACCEPTEE"})

    reponse = stats(client, evenement_id)

    assert reponse.status_code == 200
    assert reponse.json() == {
        "evenement_id": evenement_id,
        "total": 10,
        "en_attente": 5,
        "acceptees": 3,
        "refusees": 2,
        "pourcentage_acceptation": 30.0,
        "pourcentage_refus": 20.0,
    }


def test_statistiques_arrondies_a_deux_decimales(client, creer_invite, creer_evenement, associer):
    evenement_id = creer_evenement()
    for i, statut in enumerate(["ACCEPTEE", "REFUSEE", "EN_ATTENTE"]):
        invitation_id = associer(evenement_id, creer_invite(nom=f"I{i}", email=f"i{i}@example.com"))
        client.patch(f"/api/v1/invitations/{invitation_id}/statut", json={"statut": statut})
    corps = stats(client, evenement_id).json()
    assert corps["pourcentage_acceptation"] == 33.33 and corps["pourcentage_refus"] == 33.33


def test_evenement_sans_invitation_pas_de_division_par_zero(client, creer_evenement):
    reponse = stats(client, creer_evenement())
    assert reponse.status_code == 200
    corps = reponse.json()
    assert (corps["total"], corps["pourcentage_acceptation"], corps["pourcentage_refus"]) == (0, 0.0, 0.0)


def test_statistiques_dun_evenement_inexistant(client):
    reponse = stats(client, 999)
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "EVENEMENT_INTROUVABLE"


@pytest.mark.parametrize("identifiant", ["0", "abc"])
def test_statistiques_identifiant_invalide(client, identifiant):
    assert stats(client, identifiant).status_code == 422
