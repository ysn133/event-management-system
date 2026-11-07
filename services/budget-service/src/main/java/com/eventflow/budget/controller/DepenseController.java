package com.eventflow.budget.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.eventflow.budget.service.DepenseService;
import com.eventflow.budget.dto.DepenseCreateRequest;
import com.eventflow.budget.dto.DepenseResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/budgets/{budgetId}/depenses")
public class DepenseController {

    private final DepenseService depenseService;

    public DepenseController(DepenseService depenseService) {
        this.depenseService = depenseService;
    }

    @PostMapping
    public ResponseEntity<DepenseResponse> create(
            @PathVariable Long budgetId,
            @Valid @RequestBody DepenseCreateRequest request) {
        DepenseResponse response = depenseService.create(budgetId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/depenses/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<DepenseResponse> listByBudget(@PathVariable Long budgetId) {
        return depenseService.listByBudget(budgetId);
    }
}
