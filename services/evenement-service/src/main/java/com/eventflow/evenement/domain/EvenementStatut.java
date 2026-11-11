package com.eventflow.evenement.domain;

import java.util.Set;

public enum EvenementStatut {
    PLANIFIE,
    CONFIRME,
    EN_COURS,
    TERMINE,
    ANNULE;

    public static final String VALID_VALUES = "PLANIFIE|CONFIRME|EN_COURS|TERMINE|ANNULE";

    public boolean canTransitionTo(EvenementStatut target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PLANIFIE -> Set.of(CONFIRME, ANNULE).contains(target);
            case CONFIRME -> Set.of(EN_COURS, ANNULE).contains(target);
            case EN_COURS -> Set.of(TERMINE, ANNULE).contains(target);
            case TERMINE, ANNULE -> false;
        };
    }

    public boolean isTerminal() {
        return this == TERMINE || this == ANNULE;
    }
}
