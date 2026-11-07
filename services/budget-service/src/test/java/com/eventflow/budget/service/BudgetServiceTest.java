package com.eventflow.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.dto.BilanFinancierResponse;
import com.eventflow.budget.dto.BudgetCreateRequest;
import com.eventflow.budget.dto.BudgetResponse;
import com.eventflow.budget.dto.BudgetUpdateRequest;
import com.eventflow.budget.exception.BudgetAlreadyExistsException;
import com.eventflow.budget.exception.BudgetNotFoundException;
import com.eventflow.budget.exception.EventNotFoundException;
import com.eventflow.budget.integration.EventValidationPort;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final Long BUDGET_ID = 10L;
    private static final Long EVENT_ID = 20L;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private DepenseRepository depenseRepository;

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
        BilanFinancierResponse response = calculateReport("5000.00", "0.00");

        assertThat(response.totalDepenses()).isEqualByComparingTo("0.00");
        assertThat(response.soldeRestant()).isEqualByComparingTo("5000.00");
        assertThat(response.depassement()).isFalse();
    }

    @Test
    void calculatesPositiveBalanceWhenExpensesAreBelowBudget() {
        BilanFinancierResponse response = calculateReport("5000.00", "1250.50");

        assertThat(response.totalDepenses()).isEqualByComparingTo("1250.50");
        assertThat(response.soldeRestant()).isEqualByComparingTo("3749.50");
        assertThat(response.depassement()).isFalse();
    }

    @Test
    void calculatesZeroBalanceWithoutOverspending() {
        BilanFinancierResponse response = calculateReport("5000.00", "5000.00");

        assertThat(response.soldeRestant()).isEqualByComparingTo("0.00");
        assertThat(response.depassement()).isFalse();
    }

    @Test
    void reportsOverspendingAndNegativeBalance() {
        BilanFinancierResponse response = calculateReport("5000.00", "5750.00");

        assertThat(response.soldeRestant()).isEqualByComparingTo("-750.00");
        assertThat(response.depassement()).isTrue();
    }

    @Test
    void rejectsReportWhenBudgetDoesNotExist() {
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBilan(BUDGET_ID))
                .isInstanceOf(BudgetNotFoundException.class);

        verify(depenseRepository, never()).sumMontantByBudgetId(any());
    }

    private BilanFinancierResponse calculateReport(String budgetAmount, String expenseTotal) {
        Budget budget = budget(BUDGET_ID, EVENT_ID, budgetAmount);
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));
        when(depenseRepository.sumMontantByBudgetId(BUDGET_ID)).thenReturn(new BigDecimal(expenseTotal));

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
}
