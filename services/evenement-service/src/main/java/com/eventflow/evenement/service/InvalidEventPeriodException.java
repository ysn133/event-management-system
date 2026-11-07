package com.eventflow.evenement.service;

public class InvalidEventPeriodException extends RuntimeException {

    public InvalidEventPeriodException() {
        super("Event end date must be after its start date");
    }
}
