package com.eventflow.evenement.dto;

import com.eventflow.evenement.domain.EvenementStatut;

import jakarta.validation.constraints.NotNull;

public record EvenementStatusUpdateRequest(
        @NotNull(message = "must not be null")
        EvenementStatut statut) {
}
