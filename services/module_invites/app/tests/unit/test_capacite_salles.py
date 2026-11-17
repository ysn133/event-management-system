"""Règle « les invités ne dépassent pas la capacité des salles » (dépôts et client Salles factices)."""

import pytest

from app.core.exceptions import ConflictError, ServiceUnavailableError
from app.core.enums import StatutInvitation
from app.services.association_service import AssociationService
from app.services.capacite import places_occupees
from app.services.invitation_service import InvitationService
from app.services.statistiques_service import StatistiquesService
from app.tests.unit.fakes import Monde


@pytest.fixture
def monde():
    return Monde()


@pytest.fixture
def association(monde):
    return AssociationService(monde.session, monde.invites, monde.evenements, monde.invitations, monde.salles)


@pytest.fixture
def invitation_service(monde):
    return InvitationService(monde.session, monde.invites, monde.evenements, monde.invitations, monde.sender, monde.salles)


def _associer(monde, association, evenement, n=1):
    for i in range(n):
        association.associer(evenement.id, monde.inviter(nom=f"Invité {i}", email=f"i{i}@ex.com").id)


def test_places_occupees_ignore_les_refus():
    assert places_occupees({"en_attente": 2, "acceptees": 3, "refusees": 4, "total": 9}) == 5


def test_association_sous_la_capacite(monde, association):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 3)
    _associer(monde, association, evenement, 3)  # pile à la capacité : autorisé
    assert len(monde.invitations.items) == 3


def test_association_au_dela_de_la_capacite_est_refusee(monde, association):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 2)
    _associer(monde, association, evenement, 2)
    with pytest.raises(ConflictError) as exc:
        association.associer(evenement.id, monde.inviter(nom="Trop", email="t@ex.com").id)
    assert exc.value.code == "CAPACITE_SALLE_DEPASSEE"
    assert len(monde.invitations.items) == 2


def test_un_refus_libere_une_place(monde, association, invitation_service):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 1)
    _associer(monde, association, evenement)
    invitation = next(iter(monde.invitations.items.values()))
    invitation_service.changer_statut(invitation.id, StatutInvitation.REFUSEE)
    _associer(monde, association, evenement)  # la place libérée est reprenable
    assert len(monde.invitations.items) == 2


def test_refusee_vers_acceptee_bloque_si_complet(monde, association, invitation_service):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 1)
    _associer(monde, association, evenement)
    premiere = next(iter(monde.invitations.items.values()))
    invitation_service.changer_statut(premiere.id, StatutInvitation.REFUSEE)
    _associer(monde, association, evenement)  # la salle est de nouveau pleine
    with pytest.raises(ConflictError) as exc:
        invitation_service.changer_statut(premiere.id, StatutInvitation.ACCEPTEE)
    assert exc.value.code == "CAPACITE_SALLE_DEPASSEE"
    assert premiere.statut == "REFUSEE"


def test_changement_entre_statuts_occupant_une_place_ne_verifie_pas(monde, association, invitation_service):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 1)
    _associer(monde, association, evenement)
    invitation = next(iter(monde.invitations.items.values()))
    monde.salles.panne = ServiceUnavailableError("SERVICE_SALLES_INDISPONIBLE", "arrêté")
    invitation_service.changer_statut(invitation.id, StatutInvitation.ACCEPTEE)  # ne consulte pas Salles
    assert invitation.statut == "ACCEPTEE"


def test_aucune_salle_aucune_limite(monde, association):
    evenement = monde.evenements.ajouter()
    _associer(monde, association, evenement, 5)
    assert len(monde.invitations.items) == 5


def test_plusieurs_salles_additionnent_les_capacites(monde, association):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 1, 2)
    _associer(monde, association, evenement, 3)
    with pytest.raises(ConflictError):
        _associer(monde, association, evenement)


def test_service_salles_arrete_refuse_l_association_sans_rien_inserer(monde, association):
    evenement = monde.evenements.ajouter()
    monde.salles.panne = ServiceUnavailableError("SERVICE_SALLES_INDISPONIBLE", "arrêté")
    with pytest.raises(ServiceUnavailableError):
        _associer(monde, association, evenement)
    assert monde.invitations.items == {}


def test_la_capacite_est_propre_a_chaque_evenement(monde, association):
    a, b = monde.evenements.ajouter(), monde.evenements.ajouter()
    monde.salles.definir_capacites(a.id, 1)
    _associer(monde, association, a)
    _associer(monde, association, b, 3)  # b n'a pas de salle : indépendant de a


def test_le_verrou_est_pris_avant_le_comptage(monde, association):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 2)
    _associer(monde, association, evenement)
    assert monde.invitations.verrous == 1


def test_statistiques_exposent_la_capacite(monde, association):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 5)
    _associer(monde, association, evenement, 2)
    stats = StatistiquesService(monde.evenements, monde.invitations, monde.salles).pour_evenement(evenement.id)
    assert (stats.places_occupees, stats.capacite, stats.places_restantes, stats.capacite_depassee) == (2, 5, 3, False)


def test_statistiques_capacite_depassee_signalee(monde):
    evenement = monde.evenements.ajouter()
    monde.salles.definir_capacites(evenement.id, 1)
    for i in range(2):  # insertion directe : contourne la règle (ex. salle réduite après coup)
        monde.invitations.add(monde.inviter(nom=f"x{i}", email=f"x{i}@ex.com").id, evenement.id)
    stats = StatistiquesService(monde.evenements, monde.invitations, monde.salles).pour_evenement(evenement.id)
    assert stats.places_restantes == 0 and stats.capacite_depassee is True


def test_statistiques_degradees_si_salles_arrete(monde):
    evenement = monde.evenements.ajouter()
    monde.salles.panne = ServiceUnavailableError("SERVICE_SALLES_INDISPONIBLE", "arrêté")
    stats = StatistiquesService(monde.evenements, monde.invitations, monde.salles).pour_evenement(evenement.id)
    assert stats.capacite is None and stats.places_restantes is None and stats.capacite_depassee is False


def test_statistiques_sans_salle(monde):
    evenement = monde.evenements.ajouter()
    stats = StatistiquesService(monde.evenements, monde.invitations, monde.salles).pour_evenement(evenement.id)
    assert stats.capacite is None and stats.places_restantes is None
