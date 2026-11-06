package com.eventflow.budget.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.integration.EventValidationPort;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.web.dto.BudgetCreateRequest;
import com.eventflow.budget.web.dto.BudgetResponse;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final EventValidationPort eventValidationPort;

    public BudgetService(
            BudgetRepository budgetRepository,
            EventValidationPort eventValidationPort) {
        this.budgetRepository = budgetRepository;
        this.eventValidationPort = eventValidationPort;
    }

    @Transactional
    public BudgetResponse create(BudgetCreateRequest request) {
        if (!eventValidationPort.existsById(request.evenementId())) {
            throw new EventNotFoundException(request.evenementId());
        }

        if (budgetRepository.existsByEvenementId(request.evenementId())) {
            throw new BudgetAlreadyExistsException(request.evenementId());
        }

        try {
            Budget budget = new Budget(request.evenementId(), request.montantTotal());
            return BudgetResponse.from(budgetRepository.saveAndFlush(budget));
        } catch (DataIntegrityViolationException exception) {
            throw new BudgetAlreadyExistsException(request.evenementId());
        }
    }

    @Transactional(readOnly = true)
    public BudgetResponse getById(Long id) {
        return budgetRepository.findById(id)
                .map(BudgetResponse::from)
                .orElseThrow(() -> new BudgetNotFoundException(id));
    }
}
