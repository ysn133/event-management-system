package com.eventflow.budget.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.eventflow.budget.exception.RoomServiceUnavailableException;

class HttpRoomReservationAdapterTest {

    private static final String ROOM_SERVICE_URL = "http://rooms.test";

    private MockRestServiceServer server;
    private HttpRoomReservationAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpRoomReservationAdapter(builder, ROOM_SERVICE_URL);
    }

    @Test
    void retrievesReservationsForAnEvent() {
        server.expect(once(), requestTo(ROOM_SERVICE_URL + "/api/reservations/evenement/20"))
                .andRespond(withSuccess("""
                        [{
                          "id": 40,
                          "salleId": 7,
                          "evenementId": 20,
                          "nomSalle": "Salle Atlas",
                          "dateDebut": "2026-12-15T08:00:00Z",
                          "dateFin": "2026-12-15T19:00:00Z",
                          "prix": 1250.00,
                          "statut": "CONFIRMEE",
                          "budgetVersion": 2,
                          "budgetSyncState": "SYNCED",
                          "budgetApproved": true
                        }]
                        """, MediaType.APPLICATION_JSON));

        RoomReservation reservation = adapter.listByEvent(20L).getFirst();

        assertThat(reservation.evenementId()).isEqualTo(20L);
        assertThat(reservation.nomSalle()).isEqualTo("Salle Atlas");
        assertThat(reservation.prix()).isEqualByComparingTo("1250.00");
        assertThat(reservation.statut()).isEqualTo("CONFIRMEE");
        assertThat(reservation.budgetVersion()).isEqualTo(2L);
        server.verify();
    }

    @Test
    void forwardsTheCallingUsersJwtToRooms() {
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("caller-jwt")
                .header("alg","HS256").subject("1").build();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            server.expect(requestTo(ROOM_SERVICE_URL+"/api/reservations/evenement/20"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization","Bearer caller-jwt"))
                    .andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
            assertThat(adapter.listByEvent(20L)).isEmpty();
            server.verify();
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test
    void mapsRoomServiceFailure() {
        server.expect(once(), requestTo(ROOM_SERVICE_URL + "/api/reservations/evenement/20"))
                .andRespond(withServerError());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.listByEvent(20L))
                .isInstanceOf(RoomServiceUnavailableException.class);
        server.verify();
    }
}
