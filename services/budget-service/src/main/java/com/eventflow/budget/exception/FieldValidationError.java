package com.eventflow.budget.exception;

public record FieldValidationError(String field, String message) {
}
