"""Validation des jetons du service Auth de l'équipe (HS256, issuer eventflow)."""

import base64
import hashlib
import hmac
import json
from types import SimpleNamespace

import pytest

from app.core.jwt_auth import JwtIdentityProvider, extraire_bearer

SECRET = "x" * 40
MAINTENANT = 1_800_000_000


def _b64(donnees: bytes) -> str:
    return base64.urlsafe_b64encode(donnees).rstrip(b"=").decode()


def jeton(charge=None, *, secret=SECRET, alg="HS256"):
    charge = {"sub": "12", "iss": "eventflow", "exp": MAINTENANT + 3600} if charge is None else charge
    en_tete = _b64(json.dumps({"alg": alg, "typ": "JWT"}).encode())
    corps = _b64(json.dumps(charge).encode())
    signature = _b64(hmac.new(secret.encode(), f"{en_tete}.{corps}".encode(), hashlib.sha256).digest())
    return f"{en_tete}.{corps}.{signature}"


@pytest.fixture
def fournisseur():
    return JwtIdentityProvider(SECRET, "eventflow", clock=lambda: MAINTENANT)


def test_jeton_valide(fournisseur):
    assert fournisseur.verifier(jeton()).id == 12


def test_mauvaise_signature(fournisseur):
    assert fournisseur.verifier(jeton(secret="y" * 40)) is None


def test_jeton_expire(fournisseur):
    assert fournisseur.verifier(jeton({"sub": "1", "iss": "eventflow", "exp": MAINTENANT - 1})) is None


def test_mauvais_emetteur(fournisseur):
    assert fournisseur.verifier(jeton({"sub": "1", "iss": "autre", "exp": MAINTENANT + 5})) is None


def test_expiration_absente_ou_invalide(fournisseur):
    assert fournisseur.verifier(jeton({"sub": "1", "iss": "eventflow"})) is None
    assert fournisseur.verifier(jeton({"sub": "1", "iss": "eventflow", "exp": "demain"})) is None


def test_sub_invalide(fournisseur):
    assert fournisseur.verifier(jeton({"sub": "abc", "iss": "eventflow", "exp": MAINTENANT + 5})) is None


@pytest.mark.parametrize("subject", ["0", "-1", "1.5", True, 1, "9223372036854775808"])
def test_subject_doit_etre_un_identifiant_positif(fournisseur, subject):
    assert fournisseur.verifier(jeton({"sub": subject, "iss": "eventflow", "exp": MAINTENANT + 5})) is None


@pytest.mark.parametrize("expiry", [float("nan"), float("inf"), True])
def test_expiration_non_finie_ou_booleenne_refusee(fournisseur, expiry):
    assert fournisseur.verifier(jeton({"sub": "1", "iss": "eventflow", "exp": expiry})) is None


def test_jeton_pas_encore_valide_refuse(fournisseur):
    assert fournisseur.verifier(jeton({"sub": "1", "iss": "eventflow", "exp": MAINTENANT + 100, "nbf": MAINTENANT + 50})) is None


def test_algorithme_none_refuse(fournisseur):
    en_tete = _b64(json.dumps({"alg": "none"}).encode())
    corps = _b64(json.dumps({"sub": "1", "iss": "eventflow", "exp": MAINTENANT + 5}).encode())
    assert fournisseur.verifier(f"{en_tete}.{corps}.") is None


@pytest.mark.parametrize("brut", ["", "abc", "a.b", "a.b.c.d", "....", "é.é.é"])
def test_jetons_mal_formes(fournisseur, brut):
    assert fournisseur.verifier(brut) is None


def test_secret_trop_court():
    with pytest.raises(ValueError):
        JwtIdentityProvider("court")


def test_extraction_de_l_entete():
    def requete(valeur):
        return SimpleNamespace(headers={"Authorization": valeur} if valeur is not None else {})

    assert extraire_bearer(requete("Bearer abc")) == "abc"
    assert extraire_bearer(requete("bearer abc")) == "abc"
    assert extraire_bearer(requete("Basic abc")) is None
    assert extraire_bearer(requete("Bearer ")) is None
    assert extraire_bearer(requete(None)) is None


def test_authenticate_sans_jeton(fournisseur):
    assert fournisseur.authenticate(SimpleNamespace(headers={})) is None
    assert fournisseur.authenticate(SimpleNamespace(headers={"Authorization": f"Bearer {jeton()}"})).id == 12
