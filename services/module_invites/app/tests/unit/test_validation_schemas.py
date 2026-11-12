"""Validation Pydantic : aucune donnée invalide ne doit atteindre la base."""

import pytest
from pydantic import ValidationError

from app.schemas.invitation import InvitationCreate, InvitationRead, StatutUpdate
from app.schemas.invite import InviteCreate, InviteUpdate, valider_email, valider_telephone


def test_creation_valide_avec_email_et_telephone():
    invite = InviteCreate(nom="Amine Tagarte", email="amine@example.com", telephone="+212 6 12 34 56 78")
    assert invite.nom == "Amine Tagarte"
    assert invite.email == "amine@example.com"


def test_creation_valide_avec_email_seul():
    assert InviteCreate(nom="A", email="a@example.ma").telephone is None


def test_creation_valide_avec_telephone_seul():
    assert InviteCreate(nom="A", telephone="0612345678").email is None


def test_nom_obligatoire_absent():
    with pytest.raises(ValidationError):
        InviteCreate(email="a@example.com")


@pytest.mark.parametrize("nom", ["", "   "])
def test_nom_vide_ou_espaces_refuse(nom):
    with pytest.raises(ValidationError):
        InviteCreate(nom=nom, email="a@example.com")


def test_nom_trop_long_refuse():
    with pytest.raises(ValidationError):
        InviteCreate(nom="x" * 256, email="a@example.com")


def test_nom_est_nettoye_des_espaces():
    assert InviteCreate(nom="  Amine  ", email="a@example.com").nom == "Amine"


def test_sans_email_ni_telephone_refuse():
    with pytest.raises(ValidationError) as exc:
        InviteCreate(nom="Amine")
    assert "moyen de contact" in str(exc.value)


def test_chaines_vides_comptent_comme_absentes():
    with pytest.raises(ValidationError):
        InviteCreate(nom="Amine", email="  ", telephone="")


def test_chaine_vide_devient_none_quand_un_autre_contact_existe():
    invite = InviteCreate(nom="Amine", email="a@example.com", telephone="  ")
    assert invite.telephone is None


@pytest.mark.parametrize(
    "email",
    [
        "pas-un-email",
        "@example.com",
        "a@",
        "a@@example.com",
        "a b@example.com",
        "a@example",
        "a@.example.com",
        "a@example..com",
        "a..b@example.com",
        ".a@example.com",
        "a@example.c",
        "a@-example.com",
        "a" * 65 + "@example.com",
    ],
)
def test_email_invalide_refuse(email):
    with pytest.raises(ValidationError):
        InviteCreate(nom="Amine", email=email)


@pytest.mark.parametrize(
    "email",
    ["a@example.com", "prenom.nom+tag@sous.domaine.ma", "a_b-c@ex-ample.org", "x@xn--p1ai.xn--p1ai"],
)
def test_email_valide_accepte(email):
    assert valider_email(email) == email


@pytest.mark.parametrize("telephone", ["12", "abcdefgh", "061234567x", "+" * 8, "1" * 21, "1234567890123456"])
def test_telephone_invalide_refuse(telephone):
    with pytest.raises(ValidationError):
        InviteCreate(nom="Amine", telephone=telephone)


@pytest.mark.parametrize("telephone", ["0612345678", "+212 6 12 34 56 78", "(022) 12-34-56", "06.12.34.56.78"])
def test_telephone_valide_accepte(telephone):
    assert valider_telephone(telephone) == telephone


def test_champ_inconnu_refuse():
    with pytest.raises(ValidationError):
        InviteCreate(nom="A", email="a@example.com", id=5)


def test_update_applique_les_memes_regles_que_la_creation():
    with pytest.raises(ValidationError):
        InviteUpdate(nom="Amine")


@pytest.mark.parametrize("statut", ["EN_ATTENTE", "ACCEPTEE", "REFUSEE"])
def test_statuts_autorises(statut):
    assert StatutUpdate(statut=statut).statut.value == statut


@pytest.mark.parametrize("statut", ["PEUT_ETRE", "accepte", "acceptee", "", "ENVOYEE", None, 1])
def test_statut_invalide_refuse(statut):
    with pytest.raises(ValidationError):
        StatutUpdate(statut=statut)


@pytest.mark.parametrize("invite_id", [0, -1, 9_223_372_036_854_775_808])
def test_invitation_create_identifiant_invalide(invite_id):
    with pytest.raises(ValidationError):
        InvitationCreate(invite_id=invite_id)


def test_invitation_create_canal_par_defaut_et_invalide():
    assert InvitationCreate(invite_id=1).canal.value == "EMAIL"
    with pytest.raises(ValidationError):
        InvitationCreate(invite_id=1, canal="PIGEON")


def test_etat_envoi_est_calcule_a_partir_de_date_envoi():
    from datetime import UTC, datetime

    base = {"id": 1, "invite_id": 1, "evenement_id": 1, "statut": "EN_ATTENTE"}
    non_envoyee = InvitationRead(**base, date_envoi=None)
    envoyee = InvitationRead(**base, date_envoi=datetime(2026, 1, 1, tzinfo=UTC))
    assert non_envoyee.model_dump()["etat_envoi"] == "NON_ENVOYEE"
    assert envoyee.model_dump()["etat_envoi"] == "ENVOYEE"
    # Distinct de la réponse de l'invité
    assert envoyee.statut.value == "EN_ATTENTE"


def test_nom_limite_a_150_caracteres_comme_la_colonne_de_la_base():
    """invites.nom est VARCHAR(150) dans la base commune : au-delà, PostgreSQL répondrait par une erreur 500."""
    assert InviteCreate(nom="A" * 150, email="a@example.com").nom == "A" * 150
    with pytest.raises(ValidationError):
        InviteCreate(nom="A" * 151, email="a@example.com")
