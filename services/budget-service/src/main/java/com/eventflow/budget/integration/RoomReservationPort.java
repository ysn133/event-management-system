package com.eventflow.budget.integration;

import java.util.List;

public interface RoomReservationPort {
    List<RoomReservation> listByEvent(Long eventId);
}
