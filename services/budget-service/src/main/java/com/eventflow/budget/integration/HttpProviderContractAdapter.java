package com.eventflow.budget.integration;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.eventflow.budget.exception.SourceServiceUnavailableException;

@Component
public class HttpProviderContractAdapter implements ProviderContractPort {
    private final RestClient client;
    private final boolean mock;
    public HttpProviderContractAdapter(RestClient.Builder builder,
            @Value("${integrations.providers.base-url:http://localhost:5002}") String url,
            @Value("${integrations.providers.mode:http}") String mode) {
        client=builder.baseUrl(url).build(); mock="mock".equals(mode);
    }
    public List<Contract> listByEvent(Long eventId,String authorization) {
        if (mock) return List.of();
        try {
            var result=client.get().uri("/api/contrats-prestataire/evenement/{id}",eventId)
                .header("Authorization",authorization).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<Contract>>() {});
            if (result==null) throw new SourceServiceUnavailableException();
            return result;
        } catch(RestClientException e) { throw new SourceServiceUnavailableException(); }
    }
}
