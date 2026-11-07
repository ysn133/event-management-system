package com.eventflow.budget.web.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        boolean success,
        String code,
        String message,
        List<FieldValidationError> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(false, code, message, null);
    }

    public static ApiError validation(List<FieldValidationError> fieldErrors) {
        return new ApiError(
                false,
                "VALIDATION_ERROR",
                "The request contains invalid data",
                fieldErrors);
    }
}
