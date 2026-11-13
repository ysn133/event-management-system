package com.eventflow.budget.exception;

public class BudgetClosedException extends RuntimeException {
    public BudgetClosedException(Long budgetId) {
        super("Budget " + budgetId + " is closed and cannot be modified");
    }
}
