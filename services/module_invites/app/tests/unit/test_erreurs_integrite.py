"""Classification des erreurs d'intégrité PostgreSQL (sans base : fausses exceptions).

Les messages ci-dessous sont ceux RÉELLEMENT renvoyés par PostgreSQL pour le schéma de la base
commune EventFlow (capturés avec psql) ; les noms ``uq_…`` / ``…_fkey`` sont ceux du cahier des
charges initial. Le code ne doit pas dépendre du nom des contraintes.
"""

from types import SimpleNamespace

import pytest

from app.repositories.errors import ErreurIntegrite, classer_erreur_integrite


def erreur(sqlstate, detail=None, contrainte=None):
    diag = SimpleNamespace(constraint_name=contrainte, message_detail=detail, sqlstate=sqlstate)
    return SimpleNamespace(orig=SimpleNamespace(sqlstate=sqlstate, diag=diag))


@pytest.mark.parametrize(
    ("contrainte", "attendu"),
    [
        ("uk_invitations_invite_evenement", ErreurIntegrite.ASSOCIATION_EN_DOUBLE),  # base commune
        ("uq_invitations_invite_evenement", ErreurIntegrite.ASSOCIATION_EN_DOUBLE),  # cahier des charges
        ("nom_quelconque_choisi_par_une_autre_equipe", ErreurIntegrite.ASSOCIATION_EN_DOUBLE),
    ],
)
def test_doublon_reconnu_quel_que_soit_le_nom_de_la_contrainte(contrainte, attendu):
    exc = erreur("23505", "Key (invite_id, evenement_id)=(1, 1) already exists.", contrainte)
    assert classer_erreur_integrite(exc) is attendu


@pytest.mark.parametrize(
    ("detail", "contrainte", "attendu"),
    [
        ('Key (invite_id)=(999) is not present in table "invites".', "fk_invitations_invite", ErreurIntegrite.INVITE_ABSENT),
        ('Key (invite_id)=(999) is not present in table "invites".', "invitations_invite_id_fkey", ErreurIntegrite.INVITE_ABSENT),
        ('Key (evenement_id)=(999) is not present in table "evenements".', "fk_invitations_evenement", ErreurIntegrite.EVENEMENT_ABSENT),
        ('Key (evenement_id)=(999) is not present in table "evenements".', "invitations_evenement_id_fkey", ErreurIntegrite.EVENEMENT_ABSENT),
    ],
)
def test_cle_etrangere_reconnue_par_la_colonne(detail, contrainte, attendu):
    assert classer_erreur_integrite(erreur("23503", detail, contrainte)) is attendu


def test_cle_etrangere_repli_sur_le_nom_si_le_detail_est_absent():
    assert classer_erreur_integrite(erreur("23503", None, "fk_invitations_invite")) is ErreurIntegrite.INVITE_ABSENT
    assert classer_erreur_integrite(erreur("23503", None, "fk_invitations_evenement")) is ErreurIntegrite.EVENEMENT_ABSENT


def test_suppression_dun_invite_encore_reference():
    detail = 'Key (id)=(1) is still referenced from table "invitations".'
    assert classer_erreur_integrite(erreur("23503", detail, "fk_invitations_invite")) is ErreurIntegrite.INVITE_ENCORE_REFERENCE


def test_reference_par_une_autre_table_nest_pas_une_erreur_connue():
    detail = 'Key (id)=(1) is still referenced from table "budgets".'
    assert classer_erreur_integrite(erreur("23503", detail, "fk_budgets_evenement")) is None


def test_unicite_dune_autre_table_ou_autres_colonnes_nest_pas_reconnue():
    assert classer_erreur_integrite(erreur("23505", "Key (email)=(a@x.fr) already exists.", "uk_organisateurs_email")) is None


@pytest.mark.parametrize("sqlstate", ["23514", "22001", "08006", None])
def test_autres_erreurs_ne_sont_pas_reconnues(sqlstate):
    assert classer_erreur_integrite(erreur(sqlstate, "peu importe", "ck_invitations_statut")) is None


def test_exception_sans_diagnostic_ne_plante_pas():
    assert classer_erreur_integrite(SimpleNamespace(orig=None)) is None
    assert classer_erreur_integrite(ValueError("autre")) is None
