package com.eventflow.budget.exception;

public class BudgetAlreadyExistsException extends RuntimeException {

    public BudgetAlreadyExistsException(Long eventId) {
        super("A budget already exists for event: " + eventId);
    }
}
