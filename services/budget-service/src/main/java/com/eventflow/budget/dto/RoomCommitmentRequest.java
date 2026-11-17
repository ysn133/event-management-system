package com.eventflow.budget.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record RoomCommitmentRequest(
        @NotBlank @Size(max=255) String description,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=17,fraction=2) BigDecimal montant,
        @NotNull LocalDate dateDepense,
        @NotBlank @Pattern(regexp="EN_ATTENTE|CONFIRMEE|ANNULEE") String statut,
        @NotNull @Positive Long version) {}
