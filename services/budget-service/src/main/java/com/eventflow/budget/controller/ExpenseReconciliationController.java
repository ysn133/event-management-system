package com.eventflow.budget.controller;

import org.springframework.web.bind.annotation.*;
import com.eventflow.budget.service.ExpenseReconciliationService;
import com.eventflow.budget.dto.DepenseResponse;

@RestController
@RequestMapping("/api/budgets/{budgetId}/rapprochement")
public class ExpenseReconciliationController {
    private final ExpenseReconciliationService service;
    public ExpenseReconciliationController(ExpenseReconciliationService service) { this.service=service; }
    @GetMapping
    public ExpenseReconciliationService.Preview preview(@PathVariable Long budgetId,@RequestHeader("Authorization") String authorization) {
        return service.preview(budgetId,authorization);
    }
    @PostMapping
    public DepenseResponse reconcile(@PathVariable Long budgetId,@RequestHeader("Authorization") String authorization,
            @RequestBody ExpenseReconciliationService.Decision decision) {
        return service.reconcile(budgetId,decision,authorization);
    }
}
