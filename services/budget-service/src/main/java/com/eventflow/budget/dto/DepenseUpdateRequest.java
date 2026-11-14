package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.eventflow.budget.domain.DepenseStatut;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepenseUpdateRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 17, fraction = 2) BigDecimal montant,
        @NotBlank @Size(max = 100) String categorie,
        @NotNull LocalDate dateDepense,
        @NotNull @Pattern(regexp = DepenseStatut.VALID_VALUES_PATTERN,
                message = DepenseStatut.VALID_VALUES_MESSAGE) String statut,
        @Size(max = 2000) String commentaire) {
    public DepenseUpdateRequest(String description, BigDecimal montant, String categorie,
            LocalDate dateDepense, String statut) {
        this(description, montant, categorie, dateDepense, statut, null);
    }
}
