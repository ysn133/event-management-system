package com.eventflow.budget.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.eventflow.budget.dto.*;
import com.eventflow.budget.service.ProviderContractExpenseService;

@Validated
@RestController
public class ProviderContractExpenseController {
    private final ProviderContractExpenseService service;
    private final String serviceKey;
    public ProviderContractExpenseController(ProviderContractExpenseService service,
            @Value("${CONTRACT_SERVICE_KEY:}") String serviceKey) { this.service=service; this.serviceKey=serviceKey; }

    @PutMapping("/api/budgets/evenement/{eventId}/contrats-prestataire/{contractId}")
    public DepenseResponse synchronize(@PathVariable @Positive Long eventId, @PathVariable @Positive Long contractId,
            @RequestHeader(value="X-Service-Key", defaultValue="") String key,
            @Valid @RequestBody ProviderContractExpenseRequest request) {
        if (serviceKey.isBlank() || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider service credentials required");
        return service.synchronize(eventId, contractId, request);
    }
}
