package com.eventflow.budget.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.eventflow.budget.exception.EventServiceUnavailableException;

class HttpEventValidationAdapterTest {

    private static final String EVENT_SERVICE_URL = "http://events.test";

    private MockRestServiceServer server;
    private HttpEventValidationAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpEventValidationAdapter(builder, EVENT_SERVICE_URL);
    }

    @Test
    void returnsTrueWhenEventServiceFindsEvent() {
        server.expect(once(), requestTo(EVENT_SERVICE_URL + "/api/evenements/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        assertThat(adapter.existsById(1L)).isTrue();
        server.verify();
    }

    @Test
    void returnsFalseWhenEventServiceReturnsNotFound() {
        server.expect(once(), requestTo(EVENT_SERVICE_URL + "/api/evenements/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.existsById(999L)).isFalse();
        server.verify();
    }

    @Test
    void reportsEventServiceFailureInsteadOfTreatingItAsMissingEvent() {
        server.expect(once(), requestTo(EVENT_SERVICE_URL + "/api/evenements/1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.existsById(1L))
                .isInstanceOf(EventServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void forwardsCallerJwtToEventService() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("caller-token")
                .header("alg", "HS256").subject("1").build();
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
        try {
            server.expect(requestTo(EVENT_SERVICE_URL + "/api/evenements/1"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer caller-token"))
                    .andRespond(withSuccess());
            assertThat(adapter.existsById(1L)).isTrue();
            server.verify();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void returnsFalseForNullIdWithoutCallingEventService() {
        assertThat(adapter.existsById(null)).isFalse();
        server.verify();
    }
}
