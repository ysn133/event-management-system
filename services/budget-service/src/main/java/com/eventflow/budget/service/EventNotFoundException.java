package com.eventflow.budget.service;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long eventId) {
        super("Event not found: " + eventId);
    }
}
