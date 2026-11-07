package com.eventflow.evenement.exception;

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

    @ExceptionHandler(InvalidEventPeriodException.class)
    public ResponseEntity<ApiError> handleInvalidPeriod() {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_EVENT_PERIOD",
                "Event end date must be after its start date"));
    }

    @ExceptionHandler(EvenementNotFoundException.class)
    public ResponseEntity<ApiError> handleEvenementNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "RESOURCE_NOT_FOUND",
                "Event not found"));
    }

    @ExceptionHandler(OrganisateurNotFoundException.class)
    public ResponseEntity<ApiError> handleOrganisateurNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "ORGANIZER_NOT_FOUND",
                "Organizer not found"));
    }

    @ExceptionHandler(EvenementInUseException.class)
    public ResponseEntity<ApiError> handleEvenementInUse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "EVENT_IN_USE",
                "Event cannot be deleted because another resource references it"));
    }
}
