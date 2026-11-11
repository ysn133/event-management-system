package com.eventflow.budget.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.dto.DepenseCreateRequest;
import com.eventflow.budget.dto.DepenseFilterRequest;
import com.eventflow.budget.dto.DepenseResponse;
import com.eventflow.budget.dto.DepenseStatusUpdateRequest;
import com.eventflow.budget.dto.DepenseUpdateRequest;
import com.eventflow.budget.exception.BudgetNotFoundException;
import com.eventflow.budget.exception.DepenseNotFoundException;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

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

    @Transactional(readOnly = true)
    public List<DepenseResponse> listByBudget(Long budgetId) {
        if (!budgetRepository.existsById(budgetId)) {
            throw new BudgetNotFoundException(budgetId);
        }

        return depenseRepository.findAllByBudget_IdOrderByIdAsc(budgetId)
                .stream()
                .map(DepenseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepenseResponse> listByBudget(
            Long budgetId,
            DepenseFilterRequest filters) {
        if (!filters.hasFilters()) {
            return listByBudget(budgetId);
        }
        if (!budgetRepository.existsById(budgetId)) {
            throw new BudgetNotFoundException(budgetId);
        }

        Specification<Depense> specification = (root, query, builder) ->
                builder.equal(root.get("budget").get("id"), budgetId);
        if (filters.categorie() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    builder.lower(root.get("categorie")),
                    filters.categorie().toLowerCase()));
        }
        if (filters.statut() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    root.get("statut"),
                    filters.statut()));
        }
        if (filters.dateDebut() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("dateDepense"), filters.dateDebut()));
        }
        if (filters.dateFin() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("dateDepense"), filters.dateFin()));
        }
        if (filters.montantMin() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("montant"), filters.montantMin()));
        }
        if (filters.montantMax() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("montant"), filters.montantMax()));
        }

        return depenseRepository.findAll(specification, Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(DepenseResponse::from)
                .toList();
    }

    @Transactional
    public DepenseResponse update(Long depenseId, DepenseUpdateRequest request) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new DepenseNotFoundException(depenseId));

        depense.update(
                request.description(),
                request.montant(),
                request.categorie(),
                request.dateDepense(),
                request.statut());

        return DepenseResponse.from(depenseRepository.saveAndFlush(depense));
    }

    @Transactional
    public DepenseResponse updateStatus(Long depenseId, DepenseStatusUpdateRequest request) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new DepenseNotFoundException(depenseId));

        depense.updateStatut(request.statut());

        return DepenseResponse.from(depenseRepository.saveAndFlush(depense));
    }

    @Transactional
    public void delete(Long depenseId) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new DepenseNotFoundException(depenseId));

        depenseRepository.delete(depense);
        depenseRepository.flush();
    }
}
