package com.eventflow.budget.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.eventflow.budget.exception.EventServiceUnavailableException;

@Component
@ConditionalOnProperty(
        name = "integrations.events.mode",
        havingValue = "http",
        matchIfMissing = true)
public class HttpEventValidationAdapter implements EventValidationPort {

    private final RestClient restClient;

    public HttpEventValidationAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${integrations.events.base-url:http://localhost:5001}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public boolean existsById(Long eventId) {
        if (eventId == null) {
            return false;
        }

        try {
            restClient.get()
                    .uri("/api/evenements/{id}", eventId)
                    .headers(headers -> {
                        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
                            headers.setBearerAuth(jwt.getToken().getTokenValue());
                    })
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RestClientException exception) {
            throw new EventServiceUnavailableException(exception);
        }
    }
}
