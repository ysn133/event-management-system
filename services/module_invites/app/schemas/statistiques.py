"""Schéma des statistiques d'invitations d'un événement."""

from __future__ import annotations

from pydantic import BaseModel, Field


class StatistiquesInvitations(BaseModel):
    evenement_id: int
    total: int = Field(ge=0)
    en_attente: int = Field(ge=0)
    acceptees: int = Field(ge=0)
    refusees: int = Field(ge=0)
    pourcentage_acceptation: float = Field(ge=0, le=100, description="Sur le total des invitations, 2 décimales.")
    pourcentage_refus: float = Field(ge=0, le=100, description="Sur le total des invitations, 2 décimales.")
    places_occupees: int = Field(ge=0, description="Invitations non refusées (en attente + acceptées).")
    capacite: int | None = Field(
        default=None,
        description="Capacité totale des salles réservées (hors annulées) ; null si aucune salle ou si "
        "le service Salles est indisponible.",
    )
    places_restantes: int | None = Field(default=None, ge=0, description="capacité - places occupées (jamais négatif).")
    capacite_depassee: bool = Field(default=False, description="True si les places occupées dépassent la capacité.")
