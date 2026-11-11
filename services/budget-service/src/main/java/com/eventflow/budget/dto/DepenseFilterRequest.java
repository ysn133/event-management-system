package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.eventflow.budget.domain.DepenseStatut;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepenseFilterRequest(
        @Size(max = 100, message = "must contain at most 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String categorie,

        @Pattern(
                regexp = DepenseStatut.VALID_VALUES_PATTERN,
                message = DepenseStatut.VALID_VALUES_MESSAGE)
        String statut,

        LocalDate dateDebut,
        LocalDate dateFin,

        @DecimalMin(value = "0.00", message = "must be positive or zero")
        BigDecimal montantMin,

        @DecimalMin(value = "0.00", message = "must be positive or zero")
        BigDecimal montantMax) {

    public static DepenseFilterRequest empty() {
        return new DepenseFilterRequest(null, null, null, null, null, null);
    }

    public boolean hasFilters() {
        return categorie != null
                || statut != null
                || dateDebut != null
                || dateFin != null
                || montantMin != null
                || montantMax != null;
    }
}
