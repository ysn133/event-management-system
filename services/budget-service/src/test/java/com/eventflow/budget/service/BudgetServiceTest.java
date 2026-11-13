package com.eventflow.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.domain.NiveauAlerteBudget;
import com.eventflow.budget.dto.BilanFinancierResponse;
import com.eventflow.budget.dto.BudgetCreateRequest;
import com.eventflow.budget.dto.BudgetResponse;
import com.eventflow.budget.dto.BudgetStatistiquesResponse;
import com.eventflow.budget.dto.BudgetUpdateRequest;
import com.eventflow.budget.exception.BudgetAlreadyExistsException;
import com.eventflow.budget.exception.BudgetNotFoundException;
import com.eventflow.budget.exception.EventNotFoundException;
import com.eventflow.budget.integration.EventValidationPort;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;
import com.eventflow.budget.repository.DepenseTotals;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final Long BUDGET_ID = 10L;
    private static final Long EVENT_ID = 20L;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private DepenseRepository depenseRepository;

    @Mock
    private DepenseTotals depenseTotals;

    @Mock
    private EventValidationPort eventValidationPort;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void createsBudgetForExistingEvent() {
        BudgetCreateRequest request = new BudgetCreateRequest(EVENT_ID, new BigDecimal("5000.00"));
        when(eventValidationPort.existsById(EVENT_ID)).thenReturn(true);
        when(budgetRepository.existsByEvenementId(EVENT_ID)).thenReturn(false);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> {
            Budget savedBudget = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBudget, "id", BUDGET_ID);
            return savedBudget;
        });

        BudgetResponse response = budgetService.create(request);

        assertThat(response.id()).isEqualTo(BUDGET_ID);
        assertThat(response.evenementId()).isEqualTo(EVENT_ID);
        assertThat(response.montantTotal()).isEqualByComparingTo("5000.00");

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEvenementId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().getMontantTotal()).isEqualByComparingTo("5000.00");
    }

    @Test
    void rejectsCreationWhenEventDoesNotExist() {
        BudgetCreateRequest request = new BudgetCreateRequest(EVENT_ID, new BigDecimal("5000.00"));
        when(eventValidationPort.existsById(EVENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> budgetService.create(request))
                .isInstanceOf(EventNotFoundException.class);

        verify(budgetRepository, never()).existsByEvenementId(any());
        verify(budgetRepository, never()).saveAndFlush(any());
        verifyNoInteractions(depenseRepository);
    }

    @Test
    void rejectsCreationWhenEventAlreadyHasBudget() {
        BudgetCreateRequest request = new BudgetCreateRequest(EVENT_ID, new BigDecimal("5000.00"));
        when(eventValidationPort.existsById(EVENT_ID)).thenReturn(true);
        when(budgetRepository.existsByEvenementId(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> budgetService.create(request))
                .isInstanceOf(BudgetAlreadyExistsException.class);

        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsBudgetById() {
        Budget budget = budget(BUDGET_ID, EVENT_ID, "5000.00");
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));

        BudgetResponse response = budgetService.getById(BUDGET_ID);

        assertThat(response.id()).isEqualTo(BUDGET_ID);
        assertThat(response.evenementId()).isEqualTo(EVENT_ID);
        assertThat(response.montantTotal()).isEqualByComparingTo("5000.00");
    }

    @Test
    void rejectsRetrievalWhenBudgetIdDoesNotExist() {
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getById(BUDGET_ID))
                .isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void returnsBudgetByEventId() {
        Budget budget = budget(BUDGET_ID, EVENT_ID, "5000.00");
        when(budgetRepository.findByEvenementId(EVENT_ID)).thenReturn(Optional.of(budget));

        BudgetResponse response = budgetService.getByEvenementId(EVENT_ID);

        assertThat(response.id()).isEqualTo(BUDGET_ID);
        assertThat(response.evenementId()).isEqualTo(EVENT_ID);
        assertThat(response.montantTotal()).isEqualByComparingTo("5000.00");
    }

    @Test
    void rejectsRetrievalWhenEventHasNoBudget() {
        when(budgetRepository.findByEvenementId(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getByEvenementId(EVENT_ID))
                .isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void updatesBudgetAmountWithoutChangingIdentity() {
        Budget budget = budget(BUDGET_ID, EVENT_ID, "5000.00");
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));

        BudgetResponse response = budgetService.update(
                BUDGET_ID,
                new BudgetUpdateRequest(new BigDecimal("7500.00")));

        assertThat(response.id()).isEqualTo(BUDGET_ID);
        assertThat(response.evenementId()).isEqualTo(EVENT_ID);
        assertThat(response.montantTotal()).isEqualByComparingTo("7500.00");
        assertThat(budget.getMontantTotal()).isEqualByComparingTo("7500.00");
    }

    @Test
    void rejectsUpdateWhenBudgetDoesNotExist() {
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.update(
                BUDGET_ID,
                new BudgetUpdateRequest(new BigDecimal("7500.00"))))
                .isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void calculatesReportWithoutExpenses() {
        BilanFinancierResponse response = calculateReport(
                "5000.00", "0.00", "0.00", "0.00");

        assertThat(response.totalDepenses()).isEqualByComparingTo("0.00");
        assertThat(response.totalPrevu()).isEqualByComparingTo("0.00");
        assertThat(response.totalEngage()).isEqualByComparingTo("0.00");
        assertThat(response.totalPaye()).isEqualByComparingTo("0.00");
        assertThat(response.soldeRestant()).isEqualByComparingTo("5000.00");
        assertThat(response.depassement()).isFalse();
        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("0.00");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.NORMAL);
    }

    @Test
    void calculatesTotalsByExpenseStatus() {
        BilanFinancierResponse response = calculateReport(
                "5000.00", "500.00", "1250.50", "750.00");

        assertThat(response.totalDepenses()).isEqualByComparingTo("1750.50");
        assertThat(response.totalPrevu()).isEqualByComparingTo("500.00");
        assertThat(response.totalEngage()).isEqualByComparingTo("1250.50");
        assertThat(response.totalPaye()).isEqualByComparingTo("750.00");
        assertThat(response.soldeRestant()).isEqualByComparingTo("3749.50");
        assertThat(response.depassement()).isFalse();
        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("25.01");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.NORMAL);
    }

    @Test
    void reportsAttentionFromEightyPercent() {
        BilanFinancierResponse response = calculateReport(
                "5000.00", "0.00", "4000.00", "1000.00");

        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("80.00");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.ATTENTION);
        assertThat(response.depassement()).isFalse();
    }

    @Test
    void calculatesZeroBalanceWithoutOverspending() {
        BilanFinancierResponse response = calculateReport(
                "5000.00", "2000.00", "5000.00", "2500.00");

        assertThat(response.totalDepenses()).isEqualByComparingTo("7000.00");
        assertThat(response.soldeRestant()).isEqualByComparingTo("0.00");
        assertThat(response.depassement()).isFalse();
        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("100.00");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.LIMITE_ATTEINTE);
    }

    @Test
    void reportsOverspendingAndNegativeBalance() {
        BilanFinancierResponse response = calculateReport(
                "5000.00", "1000.00", "5750.00", "3000.00");

        assertThat(response.soldeRestant()).isEqualByComparingTo("-750.00");
        assertThat(response.depassement()).isTrue();
        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("115.00");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.DEPASSE);
    }

    @Test
    void rejectsReportWhenBudgetDoesNotExist() {
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBilan(BUDGET_ID))
                .isInstanceOf(BudgetNotFoundException.class);

        verify(depenseRepository, never()).calculateTotalsByBudgetId(any());
    }

    @Test
    void calculatesBudgetStatisticsFromActiveExpenses() {
        Budget budget = budget(BUDGET_ID, EVENT_ID, "10000.00");
        List<Depense> depenses = List.of(
                depense(1L, budget, "Planned venue", "1000.00", "SALLE", "PREVUE"),
                depense(2L, budget, "Paid venue", "2000.00", "SALLE", "PAYEE"),
                depense(3L, budget, "Pending catering", "3000.00", "TRAITEUR", "EN_ATTENTE"));
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));
        when(depenseRepository.findAllByBudget_IdAndStatutNotOrderByIdAsc(
                BUDGET_ID,
                "ANNULEE")).thenReturn(depenses);

        BudgetStatistiquesResponse response = budgetService.getStatistiques(BUDGET_ID);

        assertThat(response.nombreDepenses()).isEqualTo(3);
        assertThat(response.totalDepenses()).isEqualByComparingTo("6000.00");
        assertThat(response.montantMoyen()).isEqualByComparingTo("2000.00");
        assertThat(response.depenseLaPlusElevee().id()).isEqualTo(3L);
        assertThat(response.depenseLaPlusElevee().montant()).isEqualByComparingTo("3000.00");
        assertThat(response.repartitionParCategorie()).hasSize(2);
        assertThat(response.repartitionParCategorie().getFirst().categorie()).isEqualTo("SALLE");
        assertThat(response.repartitionParCategorie().getFirst().montantTotal())
                .isEqualByComparingTo("3000.00");
        assertThat(response.repartitionParCategorie().getFirst().pourcentage())
                .isEqualByComparingTo("50.00");
        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("50.00");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.NORMAL);
    }

    @Test
    void returnsEmptyBudgetStatisticsWhenThereAreNoExpenses() {
        Budget budget = budget(BUDGET_ID, EVENT_ID, "10000.00");
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));
        when(depenseRepository.findAllByBudget_IdAndStatutNotOrderByIdAsc(
                BUDGET_ID,
                "ANNULEE")).thenReturn(List.of());

        BudgetStatistiquesResponse response = budgetService.getStatistiques(BUDGET_ID);

        assertThat(response.nombreDepenses()).isZero();
        assertThat(response.totalDepenses()).isEqualByComparingTo("0.00");
        assertThat(response.montantMoyen()).isEqualByComparingTo("0.00");
        assertThat(response.depenseLaPlusElevee()).isNull();
        assertThat(response.repartitionParCategorie()).isEmpty();
        assertThat(response.pourcentageConsommation()).isEqualByComparingTo("0.00");
        assertThat(response.niveauAlerte()).isEqualTo(NiveauAlerteBudget.NORMAL);
    }

    @Test
    void rejectsStatisticsWhenBudgetDoesNotExist() {
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getStatistiques(BUDGET_ID))
                .isInstanceOf(BudgetNotFoundException.class);

        verify(depenseRepository, never())
                .findAllByBudget_IdAndStatutNotOrderByIdAsc(any(), any());
    }

    private BilanFinancierResponse calculateReport(
            String budgetAmount,
            String plannedTotal,
            String committedTotal,
            String paidTotal) {
        Budget budget = budget(BUDGET_ID, EVENT_ID, budgetAmount);
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));
        when(depenseRepository.calculateTotalsByBudgetId(BUDGET_ID)).thenReturn(depenseTotals);
        when(depenseTotals.getTotalPrevu()).thenReturn(new BigDecimal(plannedTotal));
        when(depenseTotals.getTotalEngage()).thenReturn(new BigDecimal(committedTotal));
        when(depenseTotals.getTotalPaye()).thenReturn(new BigDecimal(paidTotal));

        BilanFinancierResponse response = budgetService.getBilan(BUDGET_ID);

        assertThat(response.budgetId()).isEqualTo(BUDGET_ID);
        assertThat(response.evenementId()).isEqualTo(EVENT_ID);
        assertThat(response.montantTotal()).isEqualByComparingTo(budgetAmount);
        return response;
    }

    private Budget budget(Long id, Long eventId, String amount) {
        Budget budget = new Budget(eventId, new BigDecimal(amount));
        ReflectionTestUtils.setField(budget, "id", id);
        return budget;
    }

    private Depense depense(
            Long id,
            Budget budget,
            String description,
            String amount,
            String category,
            String status) {
        Depense depense = new Depense(
                budget,
                description,
                new BigDecimal(amount),
                category,
                LocalDate.of(2026, 11, 20),
                status);
        ReflectionTestUtils.setField(depense, "id", id);
        return depense;
    }
}
