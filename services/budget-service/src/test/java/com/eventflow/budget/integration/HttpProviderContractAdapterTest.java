package com.eventflow.budget.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.eventflow.budget.exception.SourceServiceUnavailableException;

class HttpProviderContractAdapterTest {
    @Test void forwardsJwtAndIncludesPendingContracts() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var adapter=new HttpProviderContractAdapter(builder,"http://providers.test","http");
        server.expect(requestTo("http://providers.test/api/contrats-prestataire/evenement/20"))
            .andExpect(header("Authorization","Bearer test"))
            .andRespond(withSuccess("""
                [{"id":41,"evenementId":20,"prestation":"Buffet","montant":700,"datePrestation":"2027-01-10T12:00:00Z",
                  "statut":"CONFIRME","budgetSyncState":"PENDING","budgetSyncVersion":1,"prestataireId":12}]
                """,MediaType.APPLICATION_JSON));
        var rows=adapter.listByEvent(20L,"Bearer test");
        assertThat(rows).hasSize(1); assertThat(rows.getFirst().budgetSyncState()).isEqualTo("PENDING");
        server.verify();
    }
    @Test void aFailedOrEmptyHttpResponseIsNotAnEmptySourceList() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var adapter=new HttpProviderContractAdapter(builder,"http://providers.test","http");
        server.expect(requestTo("http://providers.test/api/contrats-prestataire/evenement/20")).andRespond(withServerError());
        assertThatThrownBy(()->adapter.listByEvent(20L,"Bearer test")).isInstanceOf(SourceServiceUnavailableException.class);
        server.verify(); server.reset();
        server.expect(requestTo("http://providers.test/api/contrats-prestataire/evenement/20")).andRespond(withNoContent());
        assertThatThrownBy(()->adapter.listByEvent(20L,"Bearer test")).isInstanceOf(SourceServiceUnavailableException.class);
        server.verify();
    }
}
