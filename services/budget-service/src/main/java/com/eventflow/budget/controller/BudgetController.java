package com.eventflow.budget.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.eventflow.budget.service.BudgetService;
import com.eventflow.budget.dto.BilanFinancierResponse;
import com.eventflow.budget.dto.BudgetCreateRequest;
import com.eventflow.budget.dto.BudgetResponse;
import com.eventflow.budget.dto.BudgetStatistiquesResponse;
import com.eventflow.budget.dto.BudgetUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetCreateRequest request) {
        BudgetResponse response = budgetService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public BudgetResponse getById(@PathVariable Long id) {
        return budgetService.getById(id);
    }

    @GetMapping("/evenement/{evenementId}")
    public BudgetResponse getByEvenementId(@PathVariable Long evenementId) {
        return budgetService.getByEvenementId(evenementId);
    }

    @PutMapping("/{id}")
    public BudgetResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BudgetUpdateRequest request) {
        return budgetService.update(id, request);
    }

    @PostMapping("/{id}/cloturer")
    public BudgetResponse close(@PathVariable Long id) {
        return budgetService.close(id);
    }

    @PostMapping("/{id}/rouvrir")
    public BudgetResponse reopen(@PathVariable Long id) {
        return budgetService.reopen(id);
    }

    @GetMapping("/{id}/bilan")
    public BilanFinancierResponse getBilan(@PathVariable Long id) {
        return budgetService.getBilan(id);
    }

    @GetMapping("/{id}/statistiques")
    public BudgetStatistiquesResponse getStatistiques(@PathVariable Long id) {
        return budgetService.getStatistiques(id);
    }
}
