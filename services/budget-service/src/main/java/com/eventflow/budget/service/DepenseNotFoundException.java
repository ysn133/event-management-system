package com.eventflow.budget.service;

public class DepenseNotFoundException extends RuntimeException {

    public DepenseNotFoundException(Long depenseId) {
        super("Expense not found: " + depenseId);
    }
}
