package com.eventflow.budget.exception;

public class BudgetExceededException extends RuntimeException {
    public BudgetExceededException(String message) { super(message); }
}
