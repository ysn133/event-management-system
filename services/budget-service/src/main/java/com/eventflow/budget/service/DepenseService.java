package com.eventflow.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;
import com.eventflow.budget.web.dto.DepenseCreateRequest;
import com.eventflow.budget.web.dto.DepenseResponse;

@Service
public class DepenseService {

    private final BudgetRepository budgetRepository;
    private final DepenseRepository depenseRepository;

    public DepenseService(
            BudgetRepository budgetRepository,
            DepenseRepository depenseRepository) {
        this.budgetRepository = budgetRepository;
        this.depenseRepository = depenseRepository;
    }

    @Transactional
    public DepenseResponse create(Long budgetId, DepenseCreateRequest request) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));

        Depense depense = new Depense(
                budget,
                request.description(),
                request.montant(),
                request.categorie(),
                request.dateDepense(),
                request.statut());

        return DepenseResponse.from(depenseRepository.saveAndFlush(depense));
    }
}
