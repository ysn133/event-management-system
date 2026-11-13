package com.eventflow.budget.dto;

import java.math.BigDecimal;

public record CategorieStatistiqueResponse(
        String categorie,
        BigDecimal montantTotal,
        BigDecimal pourcentage) {
}
