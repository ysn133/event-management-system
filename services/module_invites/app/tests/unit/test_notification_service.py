"""Services d'envoi : simulation explicite, jamais d'affirmation d'envoi réel sans fournisseur."""

from datetime import UTC, datetime

import pytest

from app.core.enums import CanalEnvoi
from app.core.exceptions import NotificationNotConfiguredError
from app.services.notification_service import (
    Destinataire,
    MessageInvitation,
    NotConfiguredSender,
    NotificationSender,
    SimulatedNotificationSender,
    construire_sender,
)

DESTINATAIRE = Destinataire(nom="Amine", email="amine@example.com", telephone="0612345678")
MESSAGE = MessageInvitation(
    invitation_id=1,
    evenement_titre="Conférence",
    evenement_lieu="Salle A",
    evenement_date_debut=datetime(2026, 12, 1, tzinfo=UTC),
)


def test_envoi_simule_confirme_et_signale_la_simulation():
    sender = SimulatedNotificationSender()
    resultat = sender.envoyer(CanalEnvoi.EMAIL, DESTINATAIRE, MESSAGE)
    assert resultat.confirme is True
    assert resultat.simulation is True
    assert resultat.fournisseur == "simulation"
    assert sender.envois == [(CanalEnvoi.EMAIL, DESTINATAIRE, MESSAGE)]


def test_echec_simule_nest_pas_confirme_et_nest_pas_memorise():
    sender = SimulatedNotificationSender(echec=True)
    resultat = sender.envoyer(CanalEnvoi.SMS, DESTINATAIRE, MESSAGE)
    assert resultat.confirme is False
    assert resultat.simulation is True
    assert resultat.erreur
    assert sender.envois == []


def test_sans_fournisseur_lenvoi_est_refuse_avec_503():
    with pytest.raises(NotificationNotConfiguredError) as exc:
        NotConfiguredSender().envoyer(CanalEnvoi.EMAIL, DESTINATAIRE, MESSAGE)
    assert exc.value.status_code == 503
    assert exc.value.code == "FOURNISSEUR_ENVOI_NON_CONFIGURE"


def test_construire_sender():
    assert isinstance(construire_sender("none"), NotConfiguredSender)
    assert isinstance(construire_sender("simulated"), SimulatedNotificationSender)
    with pytest.raises(ValueError):
        construire_sender("smtp")


def test_interface_abstraite_non_instanciable():
    with pytest.raises(TypeError):
        NotificationSender()  # type: ignore[abstract]
