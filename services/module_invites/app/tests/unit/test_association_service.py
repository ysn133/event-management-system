"""Association invité ↔ événement (dépôts factices)."""

from datetime import UTC, datetime

import pytest

from app.core.enums import StatutInvitation
from app.core.exceptions import ConflictError, NotFoundError
from app.services.association_service import AssociationService
from app.tests.unit.fakes import Monde


@pytest.fixture
def monde():
    return Monde()


@pytest.fixture
def service(monde):
    return AssociationService(monde.session, monde.invites, monde.evenements, monde.invitations)


def test_association_valide_cree_une_invitation_en_attente_non_envoyee(service, monde):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    invitation = service.associer(evenement.id, invite.id)
    assert invitation.statut == "EN_ATTENTE"
    assert invitation.date_envoi is None
    assert monde.session.commits == 1


def test_association_evenement_inexistant_leve_404(service, monde):
    invite = monde.inviter()
    with pytest.raises(NotFoundError) as exc:
        service.associer(999, invite.id)
    assert exc.value.code == "EVENEMENT_INTROUVABLE"
    assert monde.invitations.items == {}


def test_association_invite_inexistant_leve_404(service, monde):
    evenement = monde.evenements.ajouter()
    with pytest.raises(NotFoundError) as exc:
        service.associer(evenement.id, 999)
    assert exc.value.code == "INVITE_INTROUVABLE"


def test_association_en_double_leve_409(service, monde):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    service.associer(evenement.id, invite.id)
    with pytest.raises(ConflictError) as exc:
        service.associer(evenement.id, invite.id)
    assert exc.value.status_code == 409
    assert exc.value.code == "ASSOCIATION_EXISTANTE"
    assert len(monde.invitations.items) == 1


def test_conflit_leve_par_le_depot_annule_la_transaction(service, monde, monkeypatch):
    """Simule la course : le contrôle applicatif passe, mais la contrainte UNIQUE échoue."""
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    monde.invitations.add(invite.id, evenement.id)
    monkeypatch.setattr(monde.invitations, "get_by_invite_and_event", lambda *a: None)
    with pytest.raises(ConflictError):
        service.associer(evenement.id, invite.id)
    assert monde.session.rollbacks == 1
    assert monde.session.commits == 0


def test_un_invite_peut_participer_a_plusieurs_evenements(service, monde):
    invite = monde.inviter()
    e1, e2 = monde.evenements.ajouter(titre="E1"), monde.evenements.ajouter(titre="E2")
    service.associer(e1.id, invite.id)
    service.associer(e2.id, invite.id)
    assert len(monde.invitations.items) == 2


def test_un_evenement_peut_avoir_plusieurs_invites(service, monde):
    evenement = monde.evenements.ajouter()
    a, b = monde.inviter(nom="A"), monde.inviter(nom="B", email="b@example.com")
    service.associer(evenement.id, a.id)
    service.associer(evenement.id, b.id)
    lignes, total = service.invites_de_evenement(evenement.id, statut=None, limit=20, offset=0)
    assert total == 2 and [i.nom for i, _ in lignes] == ["A", "B"]


def test_liste_des_invites_dun_evenement_inexistant_leve_404(service):
    with pytest.raises(NotFoundError):
        service.invites_de_evenement(7, statut=None, limit=20, offset=0)


def test_liste_des_evenements_dun_invite_inexistant_leve_404(service):
    with pytest.raises(NotFoundError):
        service.evenements_de_invite(7, statut=None, limit=20, offset=0)


def test_dissocier_une_invitation_en_attente(service, monde):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    service.associer(evenement.id, invite.id)
    service.dissocier(evenement.id, invite.id)
    assert monde.invitations.items == {}


def test_dissocier_association_inexistante_leve_404(service, monde):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    with pytest.raises(NotFoundError) as exc:
        service.dissocier(evenement.id, invite.id)
    assert exc.value.code == "ASSOCIATION_INTROUVABLE"


def test_dissocier_evenement_inexistant_leve_404(service, monde):
    invite = monde.inviter()
    with pytest.raises(NotFoundError) as exc:
        service.dissocier(42, invite.id)
    assert exc.value.code == "EVENEMENT_INTROUVABLE"


def test_dissocier_refuse_si_deja_envoyee(service, monde):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    invitation = service.associer(evenement.id, invite.id)
    invitation.date_envoi = datetime(2026, 11, 7, tzinfo=UTC)
    with pytest.raises(ConflictError) as exc:
        service.dissocier(evenement.id, invite.id)
    assert exc.value.code == "ASSOCIATION_NON_SUPPRIMABLE"
    assert len(monde.invitations.items) == 1


@pytest.mark.parametrize("statut", [StatutInvitation.ACCEPTEE, StatutInvitation.REFUSEE])
def test_dissocier_refuse_si_reponse_enregistree(service, monde, statut):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    invitation = service.associer(evenement.id, invite.id)
    invitation.statut = statut.value
    with pytest.raises(ConflictError):
        service.dissocier(evenement.id, invite.id)


def test_dissocier_course_avec_un_envoi_concurrent_ne_supprime_rien(service, monde):
    """Le contrôle du service passe, puis un envoi est enregistré AVANT le DELETE conditionnel."""
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    invitation = service.associer(evenement.id, invite.id)
    rollbacks_avant = monde.session.rollbacks
    delete_reel = monde.invitations.delete_if_pristine

    def delete_apres_un_envoi_concurrent(inv):
        inv.date_envoi = datetime(2026, 11, 7, tzinfo=UTC)  # l'envoi arrive entre-temps
        return delete_reel(inv)

    monde.invitations.delete_if_pristine = delete_apres_un_envoi_concurrent
    with pytest.raises(ConflictError) as exc:
        service.dissocier(evenement.id, invite.id)
    assert exc.value.code == "ASSOCIATION_NON_SUPPRIMABLE"
    assert invitation.id in monde.invitations.items
    assert monde.session.rollbacks == rollbacks_avant + 1


def test_evenements_dun_invite_viennent_du_service_evenements(service, monde):
    invite = monde.inviter()
    e1, e2 = monde.evenements.ajouter(titre="A"), monde.evenements.ajouter(titre="B")
    service.associer(e1.id, invite.id)
    service.associer(e2.id, invite.id)
    lignes, total = service.evenements_de_invite(invite.id, statut=None, limit=20, offset=0)
    assert total == 2 and [e.titre for e, _ in lignes] == ["A", "B"]


def test_evenement_absent_du_service_est_ignore_dans_la_liste(service, monde):
    invite = monde.inviter()
    e1, e2 = monde.evenements.ajouter(titre="A"), monde.evenements.ajouter(titre="B")
    service.associer(e1.id, invite.id)
    service.associer(e2.id, invite.id)
    del monde.evenements.items[e2.id]
    lignes, _ = service.evenements_de_invite(invite.id, statut=None, limit=20, offset=0)
    assert [e.titre for e, _ in lignes] == ["A"]


def test_service_evenements_indisponible_bloque_lassociation(service, monde):
    from app.core.exceptions import ServiceUnavailableError

    invite, evenement = monde.inviter(), monde.evenements.ajouter()

    def en_panne(_):
        raise ServiceUnavailableError("SERVICE_EVENEMENTS_INDISPONIBLE", "x")

    monde.evenements.get = en_panne
    with pytest.raises(ServiceUnavailableError):
        service.associer(evenement.id, invite.id)
    assert monde.invitations.items == {}
