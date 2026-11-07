package com.eventflow.budget.integration;

public interface EventValidationPort {

    boolean existsById(Long eventId);
}
