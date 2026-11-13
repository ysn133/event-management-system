package com.eventflow.budget.domain;

import java.math.BigDecimal;

public enum NiveauAlerteBudget {
    NORMAL,
    ATTENTION,
    LIMITE_ATTEINTE,
    DEPASSE;

    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public static NiveauAlerteBudget fromAmounts(BigDecimal committed, BigDecimal budget) {
        int budgetComparison = committed.compareTo(budget);
        if (budgetComparison > 0) {
            return DEPASSE;
        }
        if (budgetComparison == 0) {
            return LIMITE_ATTEINTE;
        }
        if (committed.multiply(ONE_HUNDRED).compareTo(budget.multiply(EIGHTY)) >= 0) {
            return ATTENTION;
        }
        return NORMAL;
    }
}
