package com.eventflow.budget.exception;

public class BudgetNotFoundException extends RuntimeException {

    public BudgetNotFoundException(Long budgetId) {
        super("Budget not found: " + budgetId);
    }
}
