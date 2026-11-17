package com.eventflow.budget.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record RoomReservation(
        Long id,
        Long salleId,
        Long evenementId,
        String nomSalle,
        OffsetDateTime dateDebut,
        OffsetDateTime dateFin,
        BigDecimal prix,
        String statut,
        Long budgetVersion,
        Boolean budgetApproved,
        String budgetSyncState) {
    public RoomReservation(Long id, Long salleId, Long evenementId, String nomSalle,
            OffsetDateTime dateDebut, OffsetDateTime dateFin, BigDecimal prix, String statut,Long budgetVersion) {
        this(id,salleId,evenementId,nomSalle,dateDebut,dateFin,prix,statut,budgetVersion,true,"SYNCED");
    }
    public RoomReservation(Long id, Long salleId, Long evenementId, String nomSalle,
            OffsetDateTime dateDebut, OffsetDateTime dateFin, BigDecimal prix, String statut) {
        this(id,salleId,evenementId,nomSalle,dateDebut,dateFin,prix,statut,0L);
    }
}
