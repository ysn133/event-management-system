package com.eventflow.budget.service;

public class BudgetNotFoundException extends RuntimeException {

    public BudgetNotFoundException(Long budgetId) {
        super("Budget not found: " + budgetId);
    }
}
