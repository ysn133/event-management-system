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
    @ExceptionHandler(SourceServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleSourceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of("SOURCE_UNAVAILABLE",
                "Rapprochement indisponible : impossible de vérifier toutes les sources. Aucune absence n’est supposée."));
    }
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentUpdate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("CONCURRENT_UPDATE", "La dépense a changé. Actualisez les données et réessayez."));
    }

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

    @ExceptionHandler(EventServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleEventServiceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(
                "EVENT_SERVICE_UNAVAILABLE",
                "Event service is unavailable"));
    }

    @ExceptionHandler(RoomServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleRoomServiceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(
                "ROOM_SERVICE_UNAVAILABLE",
                "Room service is unavailable"));
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

    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<ApiError> handleBudgetExceeded(BudgetExceededException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("BUDGET_EXCEEDED", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", exception.getMessage()));
    }
}
