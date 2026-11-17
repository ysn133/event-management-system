package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.eventflow.budget.domain.Depense;

public record DepenseResponse(
        Long id,
        Long budgetId,
        String description,
        BigDecimal montant,
        String categorie,
        LocalDate dateDepense,
        String statut,
        String commentaire,
        Long reservationSalleId,
        Long contratPrestataireId,
        boolean sourceCancelled,
        boolean sourceMissing,
        Instant createdAt,
        Instant updatedAt) {
    public static DepenseResponse from(Depense depense) {
        return new DepenseResponse(
                depense.getId(), depense.getBudget().getId(), depense.getDescription(),
                depense.getMontant(), depense.getCategorie(), depense.getDateDepense(),
                depense.getStatut(), depense.getCommentaire(), depense.getReservationSalleId(),
                depense.getContratPrestataireId(),
                depense.isSourceCancelled(), depense.isSourceMissing(),
                depense.getCreatedAt(), depense.getUpdatedAt());
    }
}
