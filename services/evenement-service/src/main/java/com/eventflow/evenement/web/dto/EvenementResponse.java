package com.eventflow.evenement.web.dto;

import java.time.OffsetDateTime;

import com.eventflow.evenement.domain.Evenement;

public record EvenementResponse(
        Long id,
        Long organisateurId,
        String titre,
        String description,
        OffsetDateTime dateDebut,
        OffsetDateTime dateFin,
        String lieu,
        String statut) {

    public static EvenementResponse from(Evenement evenement) {
        return new EvenementResponse(
                evenement.getId(),
                evenement.getOrganisateurId(),
                evenement.getTitre(),
                evenement.getDescription(),
                evenement.getDateDebut(),
                evenement.getDateFin(),
                evenement.getLieu(),
                evenement.getStatut());
    }
}
