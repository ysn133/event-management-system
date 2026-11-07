package com.eventflow.evenement.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EvenementCreateRequest(
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        Long organisateurId,

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must contain at most 200 characters")
        String titre,

        String description,

        @NotNull(message = "must not be null")
        OffsetDateTime dateDebut,

        @NotNull(message = "must not be null")
        OffsetDateTime dateFin,

        @Size(max = 255, message = "must contain at most 255 characters")
        String lieu,

        @Size(max = 30, message = "must contain at most 30 characters")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String statut) {
}
