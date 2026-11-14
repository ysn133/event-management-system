package com.eventflow.budget.exception;

public class RoomServiceUnavailableException extends RuntimeException {
    public RoomServiceUnavailableException() {
        super("Room service is unavailable");
    }

    public RoomServiceUnavailableException(Throwable cause) {
        super("Room service is unavailable", cause);
    }
}
