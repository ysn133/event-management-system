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
