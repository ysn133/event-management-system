"""Fournisseur d'identité JWT compatible avec le service d'authentification de l'équipe.

Jeton HS256 émis par le service Auth (issuer ``eventflow``, ``sub`` = identifiant de l'utilisateur),
signé avec le secret partagé ``JWT_SECRET``. Implémentation en bibliothèque standard (hmac, base64).
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import math
import re
import time
from typing import Any

from app.core.security import CurrentUser

TAILLE_MIN_SECRET = 32


def extraire_bearer(request: Any) -> str | None:
    """Retourne le jeton de l'en-tête ``Authorization: Bearer ...`` (ou None)."""
    entete = request.headers.get("Authorization", "")
    schema, _, jeton = entete.partition(" ")
    jeton = jeton.strip()
    if schema.lower() != "bearer" or not jeton:
        return None
    return jeton


def _b64(segment: str) -> bytes:
    return base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))


class JwtIdentityProvider:
    def __init__(self, secret: str, issuer: str = "eventflow", clock=time.time) -> None:
        if len(secret.encode("utf-8")) < TAILLE_MIN_SECRET:
            raise ValueError(f"Le secret JWT doit faire au moins {TAILLE_MIN_SECRET} octets.")
        self._secret = secret.encode("utf-8")
        self._issuer = issuer
        self._clock = clock

    def verifier(self, jeton: str) -> CurrentUser | None:
        """Retourne l'utilisateur si le jeton est valide, sinon None (jamais d'exception)."""
        try:
            en_tete_b64, charge_b64, signature_b64 = jeton.split(".")
            en_tete = json.loads(_b64(en_tete_b64))
            charge = json.loads(_b64(charge_b64))
            signature = _b64(signature_b64)
        except (ValueError, binascii.Error, UnicodeDecodeError):
            return None
        if not isinstance(en_tete, dict) or not isinstance(charge, dict):
            return None
        if en_tete.get("alg") != "HS256":  # refuse notamment « none »
            return None
        attendu = hmac.new(self._secret, f"{en_tete_b64}.{charge_b64}".encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(attendu, signature):
            return None
        if charge.get("iss") != self._issuer:
            return None
        expiration = charge.get("exp")
        if isinstance(expiration, bool) or not isinstance(expiration, (int, float)):
            return None
        if not math.isfinite(expiration) or expiration <= self._clock():
            return None
        not_before = charge.get("nbf")
        if not_before is not None and (
            isinstance(not_before, bool) or not isinstance(not_before, (int, float))
            or not math.isfinite(not_before) or not_before > self._clock()
        ):
            return None
        subject = charge.get("sub")
        if not isinstance(subject, str) or not re.fullmatch(r"[1-9][0-9]{0,18}", subject):
            return None
        try:
            identity = int(subject)
            return CurrentUser(id=identity) if identity <= 9223372036854775807 else None
        except (KeyError, TypeError, ValueError):
            return None

    def authenticate(self, request: Any) -> CurrentUser | None:
        jeton = extraire_bearer(request)
        return self.verifier(jeton) if jeton else None
