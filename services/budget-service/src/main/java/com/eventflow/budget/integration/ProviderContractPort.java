package com.eventflow.budget.integration;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface ProviderContractPort {
    List<Contract> listByEvent(Long eventId, String authorization);
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record Contract(Long id,Long evenementId,String prestation,BigDecimal montant,
            OffsetDateTime datePrestation,String statut,String budgetSyncState,Long budgetSyncVersion) {}
}
