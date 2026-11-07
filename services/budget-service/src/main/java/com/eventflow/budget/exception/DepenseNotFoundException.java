package com.eventflow.budget.exception;

public class DepenseNotFoundException extends RuntimeException {

    public DepenseNotFoundException(Long depenseId) {
        super("Expense not found: " + depenseId);
    }
}
