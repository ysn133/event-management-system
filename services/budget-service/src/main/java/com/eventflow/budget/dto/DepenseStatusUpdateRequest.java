package com.eventflow.budget.dto;

import com.eventflow.budget.domain.DepenseStatut;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DepenseStatusUpdateRequest(
        @NotNull(message = "must not be null")
        @Pattern(
                regexp = DepenseStatut.VALID_VALUES_PATTERN,
                message = DepenseStatut.VALID_VALUES_MESSAGE)
        String statut) {
}
