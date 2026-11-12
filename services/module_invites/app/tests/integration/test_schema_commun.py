"""Le module n'a pas de migration : il suppose le schéma de l'équipe (``database/init/001_schema.sql``).

Ces tests échouent si le schéma partagé change d'une façon qui casserait le module.
"""

from sqlalchemy import inspect


def test_les_tables_utilisees_existent(engine):
    assert {"invites", "invitations", "evenements"} <= set(inspect(engine).get_table_names())


def test_colonnes_conformes_au_schema_commun(engine):
    inspecteur = inspect(engine)
    invitations = {c["name"]: c for c in inspecteur.get_columns("invitations")}
    assert set(invitations) == {"id", "invite_id", "evenement_id", "date_envoi", "statut"}
    assert invitations["date_envoi"]["nullable"] is True
    assert invitations["statut"]["nullable"] is False
    invites = {c["name"]: c for c in inspecteur.get_columns("invites")}
    assert set(invites) == {"id", "nom", "email", "telephone"}
    assert invites["nom"]["nullable"] is False
    assert invites["nom"]["type"].length == 150  # la validation Pydantic doit rester <= à cette limite


def test_unicite_et_index_attendus(engine):
    inspecteur = inspect(engine)
    unicites = {c["name"]: c["column_names"] for c in inspecteur.get_unique_constraints("invitations")}
    assert unicites == {"uk_invitations_invite_evenement": ["invite_id", "evenement_id"]}
    index_evenement = [
        i["name"] for i in inspecteur.get_indexes("invitations") if i["column_names"][:1] == ["evenement_id"]
    ]
    assert index_evenement == ["idx_invitations_evenement_id"]


def test_cles_etrangeres_du_schema_commun(engine):
    cles = {fk["name"]: fk["referred_table"] for fk in inspect(engine).get_foreign_keys("invitations")}
    assert cles == {"fk_invitations_invite": "invites", "fk_invitations_evenement": "evenements"}
