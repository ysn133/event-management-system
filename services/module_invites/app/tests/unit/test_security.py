"""Point d'intégration de l'authentification : refus par défaut, jamais d'accès automatique."""

from types import SimpleNamespace

import pytest

from app.core.exceptions import AuthenticationRequiredError, ForbiddenError
from app.core.security import (
    AccessPolicy,
    CurrentUser,
    resolve_user,
    set_identity_provider,
)


@pytest.fixture(autouse=True)
def _sans_fournisseur():
    set_identity_provider(None)
    yield
    set_identity_provider(None)


class FournisseurFixe:
    def __init__(self, user):
        self.user = user

    def authenticate(self, request):
        return self.user


def test_authentification_desactivee_aucune_identite_demandee():
    assert resolve_user(object(), auth_enabled=False) is None


def test_authentification_activee_sans_fournisseur_refuse_401():
    with pytest.raises(AuthenticationRequiredError) as exc:
        resolve_user(object(), auth_enabled=True)
    assert exc.value.status_code == 401
    assert exc.value.code == "AUTHENTIFICATION_NON_CONFIGUREE"


def test_fournisseur_ne_reconnaissant_pas_lutilisateur_refuse_401():
    set_identity_provider(FournisseurFixe(None))
    with pytest.raises(AuthenticationRequiredError) as exc:
        resolve_user(object(), auth_enabled=True)
    assert exc.value.code == "AUTHENTIFICATION_REQUISE"


def test_fournisseur_branche_retourne_lutilisateur():
    set_identity_provider(FournisseurFixe(CurrentUser(id=7)))
    assert resolve_user(object(), auth_enabled=True) == CurrentUser(id=7)


def test_politique_inactive_autorise_tout_le_monde():
    AccessPolicy().verifier_evenement(SimpleNamespace(organisateur_id=1))


def test_politique_active_autorise_lorganisateur():
    AccessPolicy(enabled=True, user=CurrentUser(id=1)).verifier_evenement(SimpleNamespace(organisateur_id=1))


def test_politique_active_refuse_un_autre_utilisateur_403():
    with pytest.raises(ForbiddenError) as exc:
        AccessPolicy(enabled=True, user=CurrentUser(id=2)).verifier_evenement(SimpleNamespace(organisateur_id=1))
    assert exc.value.status_code == 403


def test_politique_active_sans_utilisateur_401():
    with pytest.raises(AuthenticationRequiredError):
        AccessPolicy(enabled=True, user=None).verifier_evenement(SimpleNamespace(organisateur_id=1))
