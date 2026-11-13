"""Calcul des statistiques (pourcentages et service, dépôts factices)."""

import pytest

from app.core.enums import StatutInvitation
from app.core.exceptions import NotFoundError
from app.services.statistiques_service import StatistiquesService, calculer_pourcentage
from app.tests.unit.fakes import Monde


@pytest.mark.parametrize(
    ("partie", "total", "attendu"),
    [(0, 0, 0.0), (0, 10, 0.0), (5, 10, 50.0), (10, 10, 100.0), (1, 3, 33.33), (2, 3, 66.67), (1, 8, 12.5)],
)
def test_calculer_pourcentage(partie, total, attendu):
    assert calculer_pourcentage(partie, total) == attendu


def test_total_nul_ne_provoque_pas_de_division_par_zero():
    assert calculer_pourcentage(0, 0) == 0.0


def _remplir(monde: Monde, evenement, statuts: list[StatutInvitation]) -> None:
    for i, statut in enumerate(statuts):
        invite = monde.inviter(nom=f"Invité {i}", email=f"i{i}@example.com")
        invitation = monde.invitations.add(invite.id, evenement.id)
        invitation.statut = statut.value


def test_statistiques_dun_evenement():
    monde = Monde()
    evenement = monde.evenements.ajouter()
    autre = monde.evenements.ajouter(titre="Autre événement")
    _remplir(
        monde,
        evenement,
        [StatutInvitation.ACCEPTEE] * 3 + [StatutInvitation.REFUSEE] * 2 + [StatutInvitation.EN_ATTENTE] * 5,
    )
    _remplir(monde, autre, [StatutInvitation.ACCEPTEE] * 4)  # ne doit pas être compté

    stats = StatistiquesService(monde.evenements, monde.invitations).pour_evenement(evenement.id)

    assert stats.evenement_id == evenement.id
    assert (stats.total, stats.en_attente, stats.acceptees, stats.refusees) == (10, 5, 3, 2)
    assert stats.pourcentage_acceptation == 30.0
    assert stats.pourcentage_refus == 20.0


def test_statistiques_dun_evenement_sans_invitation():
    monde = Monde()
    evenement = monde.evenements.ajouter()
    stats = StatistiquesService(monde.evenements, monde.invitations).pour_evenement(evenement.id)
    assert (stats.total, stats.en_attente, stats.acceptees, stats.refusees) == (0, 0, 0, 0)
    assert stats.pourcentage_acceptation == 0.0 and stats.pourcentage_refus == 0.0


def test_statistiques_arrondi_a_deux_decimales():
    monde = Monde()
    evenement = monde.evenements.ajouter()
    _remplir(monde, evenement, [StatutInvitation.ACCEPTEE, StatutInvitation.REFUSEE, StatutInvitation.EN_ATTENTE])
    stats = StatistiquesService(monde.evenements, monde.invitations).pour_evenement(evenement.id)
    assert stats.pourcentage_acceptation == 33.33
    assert stats.pourcentage_refus == 33.33


def test_statistiques_evenement_inexistant_leve_404():
    monde = Monde()
    with pytest.raises(NotFoundError) as exc:
        StatistiquesService(monde.evenements, monde.invitations).pour_evenement(404)
    assert exc.value.code == "EVENEMENT_INTROUVABLE"
