package com.eventflow.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.eventflow.budget.dto.DepenseCreateRequest;
import com.eventflow.budget.dto.DepenseResponse;
import com.eventflow.budget.dto.DepenseUpdateRequest;
import com.eventflow.budget.exception.BudgetNotFoundException;
import com.eventflow.budget.exception.DepenseNotFoundException;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

@ExtendWith(MockitoExtension.class)
class DepenseServiceTest {

    private static final Long BUDGET_ID = 10L;
    private static final Long DEPENSE_ID = 30L;
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2026, 11, 7);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private DepenseRepository depenseRepository;

    @InjectMocks
    private DepenseService depenseService;

    @Test
    void createsExpenseEvenWhenAmountExceedsBudget() {
        Budget budget = budget(BUDGET_ID, "1000.00");
        DepenseCreateRequest request = new DepenseCreateRequest(
                "Venue",
                new BigDecimal("1500.00"),
                "LOGISTIQUE",
                EXPENSE_DATE,
                "PAYEE");
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));
        when(depenseRepository.saveAndFlush(any(Depense.class))).thenAnswer(invocation -> {
            Depense savedExpense = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedExpense, "id", DEPENSE_ID);
            return savedExpense;
        });

        DepenseResponse response = depenseService.create(BUDGET_ID, request);

        assertThat(response.id()).isEqualTo(DEPENSE_ID);
        assertThat(response.budgetId()).isEqualTo(BUDGET_ID);
        assertThat(response.montant()).isEqualByComparingTo("1500.00");

        ArgumentCaptor<Depense> captor = ArgumentCaptor.forClass(Depense.class);
        verify(depenseRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBudget()).isSameAs(budget);
        assertThat(captor.getValue().getDescription()).isEqualTo("Venue");
    }

    @Test
    void defaultsNewExpenseStatusToPlannedWhenOmitted() {
        Budget budget = budget(BUDGET_ID, "1000.00");
        DepenseCreateRequest request = new DepenseCreateRequest(
                "Venue",
                new BigDecimal("500.00"),
                "LOGISTIQUE",
                EXPENSE_DATE,
                null);
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(budget));
        when(depenseRepository.saveAndFlush(any(Depense.class))).thenAnswer(invocation -> {
            Depense savedExpense = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedExpense, "id", DEPENSE_ID);
            return savedExpense;
        });

        DepenseResponse response = depenseService.create(BUDGET_ID, request);

        assertThat(response.statut()).isEqualTo("PREVUE");
    }

    @Test
    void rejectsCreationWhenBudgetDoesNotExist() {
        DepenseCreateRequest request = new DepenseCreateRequest(
                "Venue",
                new BigDecimal("500.00"),
                "LOGISTIQUE",
                EXPENSE_DATE,
                null);
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depenseService.create(BUDGET_ID, request))
                .isInstanceOf(BudgetNotFoundException.class);

        verify(depenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsExpensesForExistingBudget() {
        Budget budget = budget(BUDGET_ID, "5000.00");
        Depense first = depense(30L, budget, "Venue", "1200.00", "LOGISTIQUE");
        Depense second = depense(31L, budget, "Catering", "800.00", "TRAITEUR");
        when(budgetRepository.existsById(BUDGET_ID)).thenReturn(true);
        when(depenseRepository.findAllByBudget_IdOrderByIdAsc(BUDGET_ID))
                .thenReturn(List.of(first, second));

        List<DepenseResponse> responses = depenseService.listByBudget(BUDGET_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(DepenseResponse::id).containsExactly(30L, 31L);
        assertThat(responses).extracting(DepenseResponse::description)
                .containsExactly("Venue", "Catering");
        assertThat(responses).allMatch(response -> response.budgetId().equals(BUDGET_ID));
    }

    @Test
    void returnsEmptyListWhenBudgetHasNoExpenses() {
        when(budgetRepository.existsById(BUDGET_ID)).thenReturn(true);
        when(depenseRepository.findAllByBudget_IdOrderByIdAsc(BUDGET_ID)).thenReturn(List.of());

        List<DepenseResponse> responses = depenseService.listByBudget(BUDGET_ID);

        assertThat(responses).isEmpty();
    }

    @Test
    void rejectsListingWhenBudgetDoesNotExist() {
        when(budgetRepository.existsById(BUDGET_ID)).thenReturn(false);

        assertThatThrownBy(() -> depenseService.listByBudget(BUDGET_ID))
                .isInstanceOf(BudgetNotFoundException.class);

        verify(depenseRepository, never()).findAllByBudget_IdOrderByIdAsc(anyLong());
    }

    @Test
    void updatesExpenseWithoutChangingBudgetAssociation() {
        Budget budget = budget(BUDGET_ID, "5000.00");
        Depense expense = depense(DEPENSE_ID, budget, "Old venue", "1000.00", "LOGISTIQUE");
        DepenseUpdateRequest request = new DepenseUpdateRequest(
                "New venue",
                new BigDecimal("1250.50"),
                "LOCATION",
                EXPENSE_DATE.plusDays(1),
                "PAYEE");
        when(depenseRepository.findById(DEPENSE_ID)).thenReturn(Optional.of(expense));
        when(depenseRepository.saveAndFlush(expense)).thenReturn(expense);

        DepenseResponse response = depenseService.update(DEPENSE_ID, request);

        assertThat(response.id()).isEqualTo(DEPENSE_ID);
        assertThat(response.budgetId()).isEqualTo(BUDGET_ID);
        assertThat(response.description()).isEqualTo("New venue");
        assertThat(response.montant()).isEqualByComparingTo("1250.50");
        assertThat(response.categorie()).isEqualTo("LOCATION");
        assertThat(response.dateDepense()).isEqualTo(EXPENSE_DATE.plusDays(1));
        assertThat(response.statut()).isEqualTo("PAYEE");
        assertThat(expense.getBudget()).isSameAs(budget);
    }

    @Test
    void rejectsUpdateWhenExpenseDoesNotExist() {
        DepenseUpdateRequest request = new DepenseUpdateRequest(
                "Venue",
                new BigDecimal("500.00"),
                "LOGISTIQUE",
                EXPENSE_DATE,
                null);
        when(depenseRepository.findById(DEPENSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depenseService.update(DEPENSE_ID, request))
                .isInstanceOf(DepenseNotFoundException.class);

        verify(depenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletesExistingExpenseAndFlushesRepository() {
        Budget budget = budget(BUDGET_ID, "5000.00");
        Depense expense = depense(DEPENSE_ID, budget, "Venue", "500.00", "LOGISTIQUE");
        when(depenseRepository.findById(DEPENSE_ID)).thenReturn(Optional.of(expense));

        depenseService.delete(DEPENSE_ID);

        verify(depenseRepository).delete(expense);
        verify(depenseRepository).flush();
    }

    @Test
    void rejectsDeletionWhenExpenseDoesNotExist() {
        when(depenseRepository.findById(DEPENSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depenseService.delete(DEPENSE_ID))
                .isInstanceOf(DepenseNotFoundException.class);

        verify(depenseRepository, never()).delete(any());
        verify(depenseRepository, never()).flush();
    }

    private Budget budget(Long id, String amount) {
        Budget budget = new Budget(20L, new BigDecimal(amount));
        ReflectionTestUtils.setField(budget, "id", id);
        return budget;
    }

    private Depense depense(
            Long id,
            Budget budget,
            String description,
            String amount,
            String category) {
        Depense expense = new Depense(
                budget,
                description,
                new BigDecimal(amount),
                category,
                EXPENSE_DATE,
                "PREVUE");
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }
}
