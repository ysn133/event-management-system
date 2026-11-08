package com.eventflow.budget.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "integrations.events.mode",
        havingValue = "mock")
public class MockEventValidationAdapter implements EventValidationPort {

    @Override
    public boolean existsById(Long eventId) {
        return eventId != null && eventId > 0;
    }
}
