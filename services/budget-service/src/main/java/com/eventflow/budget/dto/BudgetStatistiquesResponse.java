package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.util.List;

import com.eventflow.budget.domain.NiveauAlerteBudget;

public record BudgetStatistiquesResponse(
        Long budgetId,
        Long evenementId,
        int nombreDepenses,
        BigDecimal totalDepenses,
        BigDecimal montantMoyen,
        DepensePrincipaleResponse depenseLaPlusElevee,
        List<CategorieStatistiqueResponse> repartitionParCategorie,
        BigDecimal pourcentageConsommation,
        NiveauAlerteBudget niveauAlerte) {
}
