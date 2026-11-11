package com.eventflow.budget.domain;

public enum DepenseStatut {
    PREVUE,
    EN_ATTENTE,
    PAYEE,
    ANNULEE;

    public static final String VALID_VALUES_PATTERN = "PREVUE|EN_ATTENTE|PAYEE|ANNULEE";
    public static final String VALID_VALUES_MESSAGE =
            "must be one of PREVUE, EN_ATTENTE, PAYEE, ANNULEE";
}
