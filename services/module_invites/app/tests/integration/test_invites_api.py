"""Gestion des invités via l'API, sur PostgreSQL réel."""

import pytest

API = "/api/v1/invites"


def corps_valide(**surcharges):
    return {"nom": "Amine Tagarte", "email": "amine@example.com", "telephone": "0612345678", **surcharges}


def test_creation_valide(client):
    reponse = client.post(API, json=corps_valide())
    assert reponse.status_code == 201
    corps = reponse.json()
    assert corps["id"] >= 1
    assert (corps["nom"], corps["email"], corps["telephone"]) == ("Amine Tagarte", "amine@example.com", "0612345678")


def test_creation_nettoie_le_nom_et_ignore_les_chaines_vides(client):
    reponse = client.post(API, json={"nom": "  Sara  ", "email": "sara@example.com", "telephone": "  "})
    assert reponse.status_code == 201
    assert reponse.json()["nom"] == "Sara"
    assert reponse.json()["telephone"] is None


def test_creation_sans_nom(client):
    reponse = client.post(API, json={"email": "a@example.com"})
    assert reponse.status_code == 422
    assert reponse.json()["error"]["code"] == "DONNEES_INVALIDES"


def test_creation_sans_email_ni_telephone(client):
    reponse = client.post(API, json={"nom": "Amine"})
    assert reponse.status_code == 422
    assert "moyen de contact" in reponse.text


def test_creation_email_invalide(client):
    reponse = client.post(API, json=corps_valide(email="pas-un-email"))
    assert reponse.status_code == 422
    assert reponse.json()["error"]["details"][0]["champ"] == "email"


def test_creation_refuse_les_champs_inconnus(client):
    assert client.post(API, json=corps_valide(id=99)).status_code == 422


def test_les_donnees_invalides_ne_sont_pas_inserees(client):
    client.post(API, json=corps_valide(email="invalide"))
    assert client.get(API).json()["total"] == 0


def test_lecture_dun_invite_existant(client, creer_invite):
    invite_id = creer_invite(nom="Sara", email="sara@example.com")
    reponse = client.get(f"{API}/{invite_id}")
    assert reponse.status_code == 200
    assert reponse.json()["nom"] == "Sara"


def test_lecture_dun_invite_inexistant(client):
    reponse = client.get(f"{API}/999")
    assert reponse.status_code == 404
    assert reponse.json()["error"]["code"] == "INVITE_INTROUVABLE"


@pytest.mark.parametrize("identifiant", ["0", "-1", "abc", "9223372036854775808"])
def test_identifiant_invalide_donne_422(client, identifiant):
    assert client.get(f"{API}/{identifiant}").status_code == 422


def test_modification_remplace_la_ressource(client, creer_invite):
    invite_id = creer_invite(nom="Ancien", email="ancien@example.com", telephone="0612345678")
    reponse = client.put(f"{API}/{invite_id}", json={"nom": "Nouveau", "email": "nouveau@example.com"})
    assert reponse.status_code == 200
    assert reponse.json() == {"id": invite_id, "nom": "Nouveau", "email": "nouveau@example.com", "telephone": None}
    assert client.get(f"{API}/{invite_id}").json()["nom"] == "Nouveau"


def test_modification_invalide(client, creer_invite):
    invite_id = creer_invite()
    assert client.put(f"{API}/{invite_id}", json={"nom": "Sans contact"}).status_code == 422


def test_modification_dun_invite_inexistant(client):
    assert client.put(f"{API}/999", json=corps_valide()).status_code == 404


def test_suppression(client, creer_invite):
    invite_id = creer_invite()
    reponse = client.delete(f"{API}/{invite_id}")
    assert reponse.status_code == 204
    assert reponse.content == b""
    assert client.get(f"{API}/{invite_id}").status_code == 404


def test_suppression_dun_invite_inexistant(client):
    assert client.delete(f"{API}/999").status_code == 404


def test_suppression_refusee_si_invitations_liees(client, creer_invite, creer_evenement, associer):
    invite_id, evenement_id = creer_invite(), creer_evenement()
    associer(evenement_id, invite_id)
    reponse = client.delete(f"{API}/{invite_id}")
    assert reponse.status_code == 409
    assert reponse.json()["error"]["code"] == "INVITE_AVEC_INVITATIONS"
    assert client.get(f"{API}/{invite_id}").status_code == 200


def test_liste_paginee(client, creer_invite):
    for i in range(5):
        creer_invite(nom=f"Invité {i}", email=f"i{i}@example.com")
    reponse = client.get(API, params={"limit": 2, "offset": 2})
    assert reponse.status_code == 200
    corps = reponse.json()
    assert (corps["total"], corps["limit"], corps["offset"]) == (5, 2, 2)
    assert [i["nom"] for i in corps["items"]] == ["Invité 2", "Invité 3"]


@pytest.mark.parametrize("params", [{"limit": 0}, {"limit": 101}, {"offset": -1}, {"limit": "x"}])
def test_pagination_invalide(client, params):
    assert client.get(API, params=params).status_code == 422


def test_recherche_par_nom(client, creer_invite):
    creer_invite(nom="Amine Tagarte", email="amine@example.com")
    creer_invite(nom="Sara Alami", email="sara@example.com")
    reponse = client.get(f"{API}/search", params={"nom": "AMINE"})
    assert reponse.status_code == 200
    assert [i["nom"] for i in reponse.json()["items"]] == ["Amine Tagarte"]


def test_recherche_par_email(client, creer_invite):
    creer_invite(nom="Amine", email="amine@example.com")
    creer_invite(nom="Sara", email="sara@autre.ma")
    reponse = client.get(f"{API}/search", params={"email": "autre.ma"})
    assert [i["nom"] for i in reponse.json()["items"]] == ["Sara"]


def test_recherche_par_telephone(client, creer_invite):
    creer_invite(nom="Amine", telephone="0612345678")
    creer_invite(nom="Sara", telephone="0711111111")
    reponse = client.get(f"{API}/search", params={"telephone": "0612"})
    assert [i["nom"] for i in reponse.json()["items"]] == ["Amine"]


def test_recherche_combine_les_criteres_en_et(client, creer_invite):
    creer_invite(nom="Amine", email="amine@example.com")
    creer_invite(nom="Amine", email="autre@example.com")
    reponse = client.get(f"{API}/search", params={"nom": "amine", "email": "autre"})
    assert reponse.json()["total"] == 1


def test_recherche_sans_critere_donne_400(client):
    reponse = client.get(f"{API}/search")
    assert reponse.status_code == 400
    assert reponse.json()["error"]["code"] == "CRITERE_RECHERCHE_MANQUANT"


def test_recherche_echappe_les_jokers_sql(client, creer_invite):
    creer_invite(nom="Amine", email="amine@example.com")
    creer_invite(nom="100% fiable", email="x@example.com")
    assert client.get(f"{API}/search", params={"nom": "%"}).json()["total"] == 1
    assert client.get(f"{API}/search", params={"nom": "_"}).json()["total"] == 0


def test_recherche_sans_resultat(client):
    reponse = client.get(f"{API}/search", params={"nom": "inconnu"})
    assert reponse.status_code == 200
    assert reponse.json() == {"items": [], "total": 0, "limit": 20, "offset": 0}


def test_injection_sql_est_traitee_comme_du_texte(client, creer_invite):
    creer_invite(nom="Amine", email="amine@example.com")
    client.get(f"{API}/search", params={"nom": "'; DROP TABLE invites; --"})
    assert client.get(API).json()["total"] == 1
