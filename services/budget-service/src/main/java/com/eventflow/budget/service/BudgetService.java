package com.eventflow.budget.service;

import java.math.BigDecimal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.integration.EventValidationPort;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;
import com.eventflow.budget.web.dto.BilanFinancierResponse;
import com.eventflow.budget.web.dto.BudgetCreateRequest;
import com.eventflow.budget.web.dto.BudgetResponse;
import com.eventflow.budget.web.dto.BudgetUpdateRequest;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final DepenseRepository depenseRepository;
    private final EventValidationPort eventValidationPort;

    public BudgetService(
            BudgetRepository budgetRepository,
            DepenseRepository depenseRepository,
            EventValidationPort eventValidationPort) {
        this.budgetRepository = budgetRepository;
        this.depenseRepository = depenseRepository;
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

    @Transactional(readOnly = true)
    public BudgetResponse getByEvenementId(Long evenementId) {
        return budgetRepository.findByEvenementId(evenementId)
                .map(BudgetResponse::from)
                .orElseThrow(() -> new BudgetNotFoundException(evenementId));
    }

    @Transactional
    public BudgetResponse update(Long id, BudgetUpdateRequest request) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new BudgetNotFoundException(id));

        budget.updateMontantTotal(request.montantTotal());
        return BudgetResponse.from(budget);
    }

    @Transactional(readOnly = true)
    public BilanFinancierResponse getBilan(Long id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new BudgetNotFoundException(id));
        BigDecimal totalDepenses = depenseRepository.sumMontantByBudgetId(id);
        BigDecimal soldeRestant = budget.getMontantTotal().subtract(totalDepenses);

        return new BilanFinancierResponse(
                budget.getId(),
                budget.getEvenementId(),
                budget.getMontantTotal(),
                totalDepenses,
                soldeRestant,
                soldeRestant.signum() < 0);
    }
}
