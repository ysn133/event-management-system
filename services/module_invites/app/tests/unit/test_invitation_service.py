"""Invitations : envoi (simulé), statuts, listes (dépôts factices)."""

import pytest

from app.core.enums import CanalEnvoi, StatutInvitation
from app.core.exceptions import (
    BusinessValidationError,
    ConflictError,
    NotFoundError,
    NotificationFailedError,
    NotificationNotConfiguredError,
)
from app.schemas.invitation import InvitationCreate
from app.services.invitation_service import InvitationService
from app.services.notification_service import (
    NotConfiguredSender,
    NotificationSender,
    ResultatEnvoi,
    SimulatedNotificationSender,
)
from app.tests.unit.fakes import HORLOGE_FIXE, Monde


@pytest.fixture
def monde():
    return Monde()


def fabriquer(monde: Monde, sender: NotificationSender | None = None) -> InvitationService:
    return InvitationService(
        monde.session,
        monde.invites,
        monde.evenements,
        monde.invitations,
        sender or monde.sender,
        clock=lambda: HORLOGE_FIXE,
    )


@pytest.fixture
def service(monde):
    return fabriquer(monde)


@pytest.fixture
def association(monde):
    """Un invité (e-mail + téléphone) associé à un événement, invitation non envoyée."""
    invite = monde.inviter(telephone="0612345678")
    evenement = monde.evenements.ajouter()
    invitation = monde.invitations.add(invite.id, evenement.id)
    return invite, evenement, invitation


# ----------------------------------------------------------------- envoi


def test_envoi_simule_enregistre_date_envoi_et_ne_touche_pas_au_statut(service, monde, association):
    invite, evenement, invitation = association
    resultat_invitation, resultat = service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert resultat_invitation.date_envoi == HORLOGE_FIXE
    assert resultat_invitation.statut == "EN_ATTENTE"  # la réponse de l'invité n'est pas modifiée
    assert resultat.confirme is True and resultat.simulation is True
    assert len(monde.sender.envois) == 1
    assert monde.sender.envois[0][0] == CanalEnvoi.EMAIL


def test_envoi_par_sms(service, monde, association):
    invite, evenement, _ = association
    _, resultat = service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id, canal="SMS"))
    assert resultat.canal == CanalEnvoi.SMS


def test_echec_du_fournisseur_leve_502_et_ne_renseigne_pas_date_envoi(monde, association):
    invite, evenement, invitation = association
    service = fabriquer(monde, SimulatedNotificationSender(echec=True))
    with pytest.raises(NotificationFailedError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert exc.value.status_code == 502
    assert invitation.date_envoi is None


def test_exception_du_fournisseur_devient_502_sans_fuite_de_detail(monde, association):
    class Casse(NotificationSender):
        def envoyer(self, canal, destinataire, message):
            raise RuntimeError("mot de passe smtp = secret123")

    invite, evenement, invitation = association
    service = fabriquer(monde, Casse())
    with pytest.raises(NotificationFailedError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert "secret123" not in exc.value.message
    assert invitation.date_envoi is None


def test_sans_fournisseur_leve_503_et_ne_renseigne_pas_date_envoi(monde, association):
    invite, evenement, invitation = association
    service = fabriquer(monde, NotConfiguredSender())
    with pytest.raises(NotificationNotConfiguredError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert exc.value.status_code == 503
    assert invitation.date_envoi is None


def test_envoi_non_confirme_nest_pas_enregistre(monde, association):
    class NonConfirme(NotificationSender):
        def envoyer(self, canal, destinataire, message):
            return ResultatEnvoi(confirme=False, canal=canal, fournisseur="x", simulation=False)

    invite, evenement, invitation = association
    with pytest.raises(NotificationFailedError):
        fabriquer(monde, NonConfirme()).envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert invitation.date_envoi is None


def test_envoi_sans_association_prealable_leve_409(service, monde):
    invite, evenement = monde.inviter(), monde.evenements.ajouter()
    with pytest.raises(ConflictError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert exc.value.code == "ASSOCIATION_INTROUVABLE"
    assert monde.sender.envois == []
    assert monde.invitations.items == {}  # l'envoi ne crée jamais d'association


def test_envoi_deja_envoye_leve_409_et_nenvoie_rien(service, monde, association):
    invite, evenement, _ = association
    service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    with pytest.raises(ConflictError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert exc.value.code == "INVITATION_DEJA_ENVOYEE"
    assert len(monde.sender.envois) == 1


def test_envoi_evenement_inexistant_leve_404(service, monde):
    invite = monde.inviter()
    with pytest.raises(NotFoundError) as exc:
        service.envoyer(404, InvitationCreate(invite_id=invite.id))
    assert exc.value.code == "EVENEMENT_INTROUVABLE"


def test_envoi_invite_inexistant_leve_404(service, monde):
    evenement = monde.evenements.ajouter()
    with pytest.raises(NotFoundError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=404))
    assert exc.value.code == "INVITE_INTROUVABLE"


def test_envoi_par_email_sans_email_leve_422(service, monde):
    invite = monde.inviter(nom="Tel seul", email=None, telephone="0612345678")
    evenement = monde.evenements.ajouter()
    monde.invitations.add(invite.id, evenement.id)
    with pytest.raises(BusinessValidationError) as exc:
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id, canal="EMAIL"))
    assert exc.value.status_code == 422 and exc.value.code == "CONTACT_MANQUANT"
    assert monde.sender.envois == []


def test_envoi_par_sms_sans_telephone_leve_422(service, monde):
    invite = monde.inviter(nom="Email seul", email="a@example.com", telephone=None)
    evenement = monde.evenements.ajouter()
    monde.invitations.add(invite.id, evenement.id)
    with pytest.raises(BusinessValidationError):
        service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id, canal="SMS"))


def test_conflit_a_lenregistrement_si_une_autre_requete_a_marque_lenvoi(monde, association):
    """Limite documentée : l'autre requête a gagné la course ; celle-ci reçoit 409 et annule."""
    invite, evenement, invitation = association

    class MarqueEnConcurrence(SimulatedNotificationSender):
        def envoyer(self, canal, destinataire, message):
            invitation.date_envoi = HORLOGE_FIXE  # une requête concurrente a déjà enregistré
            return super().envoyer(canal, destinataire, message)

    with pytest.raises(ConflictError) as exc:
        fabriquer(monde, MarqueEnConcurrence()).envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert exc.value.code == "INVITATION_DEJA_ENVOYEE"
    assert monde.session.rollbacks == 1


def test_la_transaction_de_lecture_est_terminee_avant_lappel_externe(monde, association):
    invite, evenement, _ = association
    commits_pendant_envoi = []

    class Espion(SimulatedNotificationSender):
        def envoyer(self, canal, destinataire, message):
            commits_pendant_envoi.append(monde.session.commits)
            return super().envoyer(canal, destinataire, message)

    fabriquer(monde, Espion()).envoyer(evenement.id, InvitationCreate(invite_id=invite.id))
    assert commits_pendant_envoi == [1]  # commit de lecture déjà fait au moment de l'appel
    assert monde.session.commits == 2  # puis commit de l'enregistrement de date_envoi


# ----------------------------------------------------------------- statuts


@pytest.mark.parametrize("statut", [StatutInvitation.ACCEPTEE, StatutInvitation.REFUSEE, StatutInvitation.EN_ATTENTE])
def test_changer_statut(service, association, statut):
    _, _, invitation = association
    resultat = service.changer_statut(invitation.id, statut)
    assert resultat.statut == statut.value


def test_changer_statut_accepte_une_chaine_valide(service, association):
    _, _, invitation = association
    assert service.changer_statut(invitation.id, "ACCEPTEE").statut == "ACCEPTEE"


@pytest.mark.parametrize("statut", ["PEUT_ETRE", "accepte", "", "ENVOYEE"])
def test_changer_statut_invalide_leve_422_et_ne_modifie_rien(service, association, statut):
    _, _, invitation = association
    with pytest.raises(BusinessValidationError) as exc:
        service.changer_statut(invitation.id, statut)
    assert exc.value.code == "STATUT_INVALIDE"
    assert invitation.statut == "EN_ATTENTE"


def test_changer_statut_invitation_inexistante_leve_404(service):
    with pytest.raises(NotFoundError):
        service.changer_statut(404, StatutInvitation.ACCEPTEE)


def test_changer_statut_nenvoie_ni_ne_marque_rien(service, monde, association):
    _, _, invitation = association
    service.changer_statut(invitation.id, StatutInvitation.ACCEPTEE)
    assert invitation.date_envoi is None
    assert monde.sender.envois == []


# ----------------------------------------------------------------- lectures


def test_obtenir_invitation_inexistante_leve_404(service):
    with pytest.raises(NotFoundError):
        service.obtenir(404)


def test_obtenir_invitation_charge_linvite(service, association):
    invite, _, invitation = association
    assert service.obtenir(invitation.id).invite.id == invite.id


def test_lister_invitations_dun_evenement_avec_filtres(service, monde, association):
    invite, evenement, invitation = association
    autre = monde.inviter(nom="Autre", email="autre@example.com")
    deuxieme = monde.invitations.add(autre.id, evenement.id)
    service.changer_statut(deuxieme.id, StatutInvitation.REFUSEE)
    service.envoyer(evenement.id, InvitationCreate(invite_id=invite.id))

    tous, total = service.lister_evenement(evenement.id, statut=None, envoyee=None, limit=20, offset=0)
    assert total == 2
    refusees, _ = service.lister_evenement(evenement.id, statut=StatutInvitation.REFUSEE, envoyee=None, limit=20, offset=0)
    assert [i.id for i in refusees] == [deuxieme.id]
    envoyees, _ = service.lister_evenement(evenement.id, statut=None, envoyee=True, limit=20, offset=0)
    assert [i.id for i in envoyees] == [invitation.id]
    non_envoyees, _ = service.lister_evenement(evenement.id, statut=None, envoyee=False, limit=20, offset=0)
    assert [i.id for i in non_envoyees] == [deuxieme.id]


def test_lister_invitations_evenement_inexistant_leve_404(service):
    with pytest.raises(NotFoundError):
        service.lister_evenement(404, statut=None, envoyee=None, limit=20, offset=0)


def test_lister_invitations_dun_invite(service, monde, association):
    invite, _, invitation = association
    autre_evenement = monde.evenements.ajouter(titre="Autre")
    monde.invitations.add(invite.id, autre_evenement.id)
    items, total = service.lister_invite(invite.id, statut=None, limit=20, offset=0)
    assert total == 2 and items[0].id == invitation.id


def test_lister_invitations_invite_inexistant_leve_404(service):
    with pytest.raises(NotFoundError):
        service.lister_invite(404, statut=None, limit=20, offset=0)
