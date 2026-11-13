"""Règles métier des invités (dépôts factices)."""

import pytest

from app.core.exceptions import BadRequestError, ConflictError, NotFoundError
from app.schemas.invite import InviteCreate, InviteUpdate
from app.services.invite_service import InviteService
from app.tests.unit.fakes import Monde


@pytest.fixture
def monde():
    return Monde()


@pytest.fixture
def service(monde):
    return InviteService(monde.session, monde.invites)


def test_creer_valide_la_transaction(service, monde):
    invite = service.creer(InviteCreate(nom="Amine", email="amine@example.com"))
    assert invite.id == 1
    assert monde.session.commits == 1
    assert monde.session.rollbacks == 0


def test_obtenir_inexistant_leve_404(service):
    with pytest.raises(NotFoundError) as exc:
        service.obtenir(99)
    assert exc.value.status_code == 404
    assert exc.value.code == "INVITE_INTROUVABLE"


def test_modifier_remplace_les_champs(service, monde):
    invite = monde.inviter(nom="Ancien", email="ancien@example.com")
    service.modifier(invite.id, InviteUpdate(nom="Nouveau", telephone="0612345678"))
    assert (invite.nom, invite.email, invite.telephone) == ("Nouveau", None, "0612345678")


def test_modifier_inexistant_leve_404(service):
    with pytest.raises(NotFoundError):
        service.modifier(5, InviteUpdate(nom="X", email="x@example.com"))


def test_supprimer(service, monde):
    invite = monde.inviter()
    service.supprimer(invite.id)
    assert invite.id not in monde.invites.items


def test_supprimer_inexistant_leve_404(service):
    with pytest.raises(NotFoundError):
        service.supprimer(5)


def test_supprimer_refuse_si_invitations_liees(service, monde):
    invite = monde.inviter()
    evenement = monde.evenements.ajouter()
    monde.invitations.add(invite.id, evenement.id)
    with pytest.raises(ConflictError) as exc:
        service.supprimer(invite.id)
    assert exc.value.code == "INVITE_AVEC_INVITATIONS"
    assert invite.id in monde.invites.items


def test_recherche_par_nom(service, monde):
    monde.inviter(nom="Amine Tagarte", email="amine@example.com")
    monde.inviter(nom="Sara Alami", email="sara@example.com")
    items, total = service.rechercher(nom="amine", email=None, telephone=None, limit=20, offset=0)
    assert total == 1 and items[0].nom == "Amine Tagarte"


def test_recherche_par_email(service, monde):
    monde.inviter(nom="Amine", email="amine@example.com")
    monde.inviter(nom="Sara", email="sara@autre.ma")
    items, total = service.rechercher(nom=None, email="autre.ma", telephone=None, limit=20, offset=0)
    assert total == 1 and items[0].nom == "Sara"


def test_recherche_par_telephone(service, monde):
    monde.inviter(nom="Amine", email=None, telephone="0612345678")
    monde.inviter(nom="Sara", email=None, telephone="0711111111")
    items, total = service.rechercher(nom=None, email=None, telephone="0612", limit=20, offset=0)
    assert total == 1 and items[0].nom == "Amine"


@pytest.mark.parametrize("criteres", [{}, {"nom": "  "}, {"email": ""}, {"nom": None, "telephone": " "}])
def test_recherche_sans_critere_leve_400(service, criteres):
    with pytest.raises(BadRequestError) as exc:
        service.rechercher(**{"nom": None, "email": None, "telephone": None, **criteres}, limit=20, offset=0)
    assert exc.value.status_code == 400
    assert exc.value.code == "CRITERE_RECHERCHE_MANQUANT"


def test_pagination_de_la_liste(service, monde):
    for i in range(5):
        monde.inviter(nom=f"Invité {i}", email=f"i{i}@example.com")
    items, total = service.lister(limit=2, offset=2)
    assert total == 5
    assert [i.nom for i in items] == ["Invité 2", "Invité 3"]


def test_supprimer_course_invitation_creee_entre_le_controle_et_la_suppression(service, monde):
    """Le dépôt refuse (DELETE conditionnel) : la FK de la base commune est ON DELETE CASCADE,
    sans cela l'invitation créée entre-temps serait effacée en silence."""
    invite = monde.inviter()
    monde.invites.delete = lambda inv: False  # une invitation est apparue après le contrôle
    rollbacks_avant = monde.session.rollbacks
    with pytest.raises(ConflictError) as exc:
        service.supprimer(invite.id)
    assert exc.value.code == "INVITE_AVEC_INVITATIONS"
    assert monde.session.rollbacks == rollbacks_avant + 1
    assert invite.id in monde.invites.items
