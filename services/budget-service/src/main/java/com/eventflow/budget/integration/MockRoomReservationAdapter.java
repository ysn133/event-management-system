package com.eventflow.budget.integration;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(name = "integrations.rooms.mode", havingValue = "mock")
public class MockRoomReservationAdapter implements RoomReservationPort {
    @Override
    public List<RoomReservation> listByEvent(Long eventId) {
        return List.of();
    }
}
