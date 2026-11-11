package com.eventflow.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.domain.DepenseStatut;
import com.eventflow.budget.domain.NiveauAlerteBudget;
import com.eventflow.budget.dto.BilanFinancierResponse;
import com.eventflow.budget.dto.BudgetCreateRequest;
import com.eventflow.budget.dto.BudgetResponse;
import com.eventflow.budget.dto.BudgetStatistiquesResponse;
import com.eventflow.budget.dto.BudgetUpdateRequest;
import com.eventflow.budget.dto.CategorieStatistiqueResponse;
import com.eventflow.budget.dto.DepensePrincipaleResponse;
import com.eventflow.budget.exception.BudgetAlreadyExistsException;
import com.eventflow.budget.exception.BudgetNotFoundException;
import com.eventflow.budget.exception.EventNotFoundException;
import com.eventflow.budget.integration.EventValidationPort;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;
import com.eventflow.budget.repository.DepenseTotals;

@Service
public class BudgetService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

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
        DepenseTotals totals = depenseRepository.calculateTotalsByBudgetId(id);
        BigDecimal totalPrevu = totals.getTotalPrevu();
        BigDecimal totalEngage = totals.getTotalEngage();
        BigDecimal totalPaye = totals.getTotalPaye();
        BigDecimal totalDepenses = totalPrevu.add(totalEngage);
        BigDecimal soldeRestant = budget.getMontantTotal().subtract(totalEngage);
        BigDecimal pourcentageConsommation = calculatePercentage(
                totalEngage,
                budget.getMontantTotal());
        NiveauAlerteBudget niveauAlerte = NiveauAlerteBudget.fromAmounts(
                totalEngage,
                budget.getMontantTotal());

        return new BilanFinancierResponse(
                budget.getId(),
                budget.getEvenementId(),
                budget.getMontantTotal(),
                totalDepenses,
                totalPrevu,
                totalEngage,
                totalPaye,
                soldeRestant,
                soldeRestant.signum() < 0,
                pourcentageConsommation,
                niveauAlerte);
    }

    @Transactional(readOnly = true)
    public BudgetStatistiquesResponse getStatistiques(Long id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new BudgetNotFoundException(id));
        List<Depense> depenses = depenseRepository
                .findAllByBudget_IdAndStatutNotOrderByIdAsc(
                        id,
                        DepenseStatut.ANNULEE.name());

        BigDecimal totalDepenses = depenses.stream()
                .map(Depense::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEngage = depenses.stream()
                .filter(depense -> DepenseStatut.EN_ATTENTE.name().equals(depense.getStatut())
                        || DepenseStatut.PAYEE.name().equals(depense.getStatut()))
                .map(Depense::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal montantMoyen = depenses.isEmpty()
                ? BigDecimal.ZERO
                : totalDepenses.divide(
                        BigDecimal.valueOf(depenses.size()),
                        2,
                        RoundingMode.HALF_UP);
        DepensePrincipaleResponse depenseLaPlusElevee = depenses.stream()
                .max(Comparator.comparing(Depense::getMontant))
                .map(DepensePrincipaleResponse::from)
                .orElse(null);

        Map<String, BigDecimal> totalsByCategory = depenses.stream()
                .collect(Collectors.groupingBy(
                        Depense::getCategorie,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Depense::getMontant,
                                BigDecimal::add)));
        List<CategorieStatistiqueResponse> repartitionParCategorie = totalsByCategory.entrySet()
                .stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new CategorieStatistiqueResponse(
                        entry.getKey(),
                        entry.getValue(),
                        calculatePercentage(entry.getValue(), totalDepenses)))
                .toList();
        BigDecimal pourcentageConsommation = calculatePercentage(
                totalEngage,
                budget.getMontantTotal());

        return new BudgetStatistiquesResponse(
                budget.getId(),
                budget.getEvenementId(),
                depenses.size(),
                totalDepenses,
                montantMoyen,
                depenseLaPlusElevee,
                repartitionParCategorie,
                pourcentageConsommation,
                NiveauAlerteBudget.fromAmounts(totalEngage, budget.getMontantTotal()));
    }

    private BigDecimal calculatePercentage(BigDecimal amount, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.multiply(ONE_HUNDRED)
                .divide(total, 2, RoundingMode.HALF_UP);
    }
}
