package com.eventflow.budget.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;

import com.eventflow.budget.exception.RoomServiceUnavailableException;

@Component
@ConditionalOnProperty(
        name = "integrations.rooms.mode",
        havingValue = "http",
        matchIfMissing = true)
public class HttpRoomReservationAdapter implements RoomReservationPort {

    private final RestClient restClient;

    public HttpRoomReservationAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${integrations.rooms.base-url:http://localhost:5242}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public List<RoomReservation> listByEvent(Long eventId) {
        try {
            List<RoomReservation> reservations = restClient.get()
                    .uri("/api/reservations/evenement/{eventId}", eventId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            return reservations == null ? List.of() : reservations;
        } catch (RestClientException exception) {
            throw new RoomServiceUnavailableException(exception);
        }
    }
}
