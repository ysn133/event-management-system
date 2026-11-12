"""Envoi des invitations : interface abstraite + implémentations.

"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime

from app.core.enums import CanalEnvoi
from app.core.exceptions import NotificationNotConfiguredError


@dataclass(frozen=True)
class Destinataire:
    nom: str
    email: str | None
    telephone: str | None


@dataclass(frozen=True)
class MessageInvitation:
    invitation_id: int
    evenement_titre: str
    evenement_lieu: str | None
    evenement_date_debut: datetime


@dataclass(frozen=True)
class ResultatEnvoi:
    """``confirme=True`` seulement si le fournisseur a confirmé l'envoi."""

    confirme: bool
    canal: CanalEnvoi
    fournisseur: str
    simulation: bool
    erreur: str | None = None


class NotificationSender(ABC):
    """Contrat d'un service d'envoi."""

    @abstractmethod
    def envoyer(
        self, canal: CanalEnvoi, destinataire: Destinataire, message: MessageInvitation
    ) -> ResultatEnvoi:
        """Envoie le message. Ne doit retourner ``confirme=True`` que si l'envoi est confirmé."""


class NotConfiguredSender(NotificationSender):
    """Valeur par défaut : aucun fournisseur d'envoi n'est branché."""

    def envoyer(
        self, canal: CanalEnvoi, destinataire: Destinataire, message: MessageInvitation
    ) -> ResultatEnvoi:
        raise NotificationNotConfiguredError(
            "FOURNISSEUR_ENVOI_NON_CONFIGURE",
            "Aucun fournisseur d'envoi (e-mail/SMS) n'est configuré : l'invitation n'a pas été envoyée.",
        )


class SimulatedNotificationSender(NotificationSender):
    """SIMULATION : mémorise les messages, n'envoie rien. ``echec=True`` simule un refus du fournisseur."""

    FOURNISSEUR = "simulation"

    def __init__(self, *, echec: bool = False) -> None:
        self.echec = echec
        self.envois: list[tuple[CanalEnvoi, Destinataire, MessageInvitation]] = []

    def envoyer(
        self, canal: CanalEnvoi, destinataire: Destinataire, message: MessageInvitation
    ) -> ResultatEnvoi:
        if self.echec:
            return ResultatEnvoi(
                confirme=False,
                canal=canal,
                fournisseur=self.FOURNISSEUR,
                simulation=True,
                erreur="Échec simulé du fournisseur.",
            )
        self.envois.append((canal, destinataire, message))
        return ResultatEnvoi(
            confirme=True, canal=canal, fournisseur=self.FOURNISSEUR, simulation=True
        )


def construire_sender(backend: str) -> NotificationSender:
    """Construit le service d'envoi selon ``NOTIFICATION_BACKEND``."""
    if backend == "simulated":
        return SimulatedNotificationSender()
    if backend == "none":
        return NotConfiguredSender()
    raise ValueError(f"Backend d'envoi inconnu : {backend!r}.")
