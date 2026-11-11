package com.eventflow.budget.exception;

public class InvalidBudgetStatusTransitionException extends RuntimeException {
    public InvalidBudgetStatusTransitionException(String currentStatus, String requestedStatus) {
        super("Budget status cannot change from " + currentStatus + " to " + requestedStatus);
    }
}
