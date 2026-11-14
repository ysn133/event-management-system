package com.eventflow.budget.exception;

public class EventServiceUnavailableException extends RuntimeException {

    public EventServiceUnavailableException(Throwable cause) {
        super("Event service is unavailable", cause);
    }
}
