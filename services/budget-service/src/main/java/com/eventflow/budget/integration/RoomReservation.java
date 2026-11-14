package com.eventflow.budget.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RoomReservation(
        Long id,
        Long salleId,
        Long evenementId,
        String nomSalle,
        OffsetDateTime dateDebut,
        OffsetDateTime dateFin,
        BigDecimal prix,
        String statut) {
}
