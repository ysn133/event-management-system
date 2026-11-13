package com.eventflow.budget.exception;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldValidationError::field))
                .toList();

        return ResponseEntity.badRequest().body(ApiError.validation(fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_REQUEST_BODY",
                "The request body is missing or malformed"));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> handleEventNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "EVENT_NOT_FOUND",
                "Event not found"));
    }

    @ExceptionHandler(BudgetAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleBudgetAlreadyExists() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "BUDGET_ALREADY_EXISTS",
                "A budget already exists for this event"));
    }

    @ExceptionHandler(BudgetNotFoundException.class)
    public ResponseEntity<ApiError> handleBudgetNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "RESOURCE_NOT_FOUND",
                "Budget not found"));
    }

    @ExceptionHandler(DepenseNotFoundException.class)
    public ResponseEntity<ApiError> handleDepenseNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "RESOURCE_NOT_FOUND",
                "Expense not found"));
    }

    @ExceptionHandler(BudgetClosedException.class)
    public ResponseEntity<ApiError> handleBudgetClosed(BudgetClosedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "BUDGET_CLOSED",
                exception.getMessage()));
    }

    @ExceptionHandler(InvalidBudgetStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidBudgetStatusTransition(
            InvalidBudgetStatusTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "INVALID_BUDGET_STATUS_TRANSITION",
                exception.getMessage()));
    }
}
