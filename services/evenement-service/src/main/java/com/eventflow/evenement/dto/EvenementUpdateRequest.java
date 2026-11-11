package com.eventflow.evenement.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.eventflow.evenement.domain.EvenementStatut;

public record EvenementUpdateRequest(
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

        @NotBlank(message = "must not be blank")
        @Size(max = 30, message = "must contain at most 30 characters")
        @Pattern(regexp = EvenementStatut.VALID_VALUES,
                message = "must be one of PLANIFIE, CONFIRME, EN_COURS, TERMINE or ANNULE")
        String statut) {
}
