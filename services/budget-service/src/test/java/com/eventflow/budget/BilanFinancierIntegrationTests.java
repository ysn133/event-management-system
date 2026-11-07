package com.eventflow.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BilanFinancierIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private DepenseRepository depenseRepository;

    @BeforeEach
    void setUp() {
        depenseRepository.deleteAll();
        budgetRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        depenseRepository.deleteAll();
        budgetRepository.deleteAll();
    }

    @Test
    void returnsFullBudgetAsBalanceWhenThereAreNoExpenses() throws Exception {
        Budget budget = saveBudget(131L, "10000.00");

        mockMvc.perform(get("/api/budgets/{id}/bilan", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId").value(budget.getId()))
                .andExpect(jsonPath("$.evenementId").value(131))
                .andExpect(jsonPath("$.montantTotal").value(10000.00))
                .andExpect(jsonPath("$.totalDepenses").value(0))
                .andExpect(jsonPath("$.soldeRestant").value(10000.00))
                .andExpect(jsonPath("$.depassement").value(false));
    }

    @Test
    void calculatesRemainingBalanceFromAllBudgetExpenses() throws Exception {
        Budget budget = saveBudget(132L, "10000.00");
        Budget otherBudget = saveBudget(133L, "5000.00");
        saveExpense(budget, "Venue", "2500.25");
        saveExpense(budget, "Catering", "1749.50");
        saveExpense(otherBudget, "Unrelated", "4999.00");

        mockMvc.perform(get("/api/budgets/{id}/bilan", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDepenses").value(4249.75))
                .andExpect(jsonPath("$.soldeRestant").value(5750.25))
                .andExpect(jsonPath("$.depassement").value(false));
    }

    @Test
    void returnsZeroBalanceWhenExpensesExactlyMatchBudget() throws Exception {
        Budget budget = saveBudget(134L, "10000.00");
        saveExpense(budget, "Venue", "4000.00");
        saveExpense(budget, "Catering", "6000.00");

        mockMvc.perform(get("/api/budgets/{id}/bilan", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDepenses").value(10000.00))
                .andExpect(jsonPath("$.soldeRestant").value(0.00))
                .andExpect(jsonPath("$.depassement").value(false));
    }

    @Test
    void reportsOverspendingAndNegativeBalance() throws Exception {
        Budget budget = saveBudget(135L, "5000.00");
        saveExpense(budget, "Venue", "3200.00");
        saveExpense(budget, "Catering", "2000.50");

        mockMvc.perform(get("/api/budgets/{id}/bilan", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDepenses").value(5200.50))
                .andExpect(jsonPath("$.soldeRestant").value(-200.50))
                .andExpect(jsonPath("$.depassement").value(true));
    }

    @Test
    void returnsNotFoundWhenBudgetDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/budgets/{id}/bilan", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Budget saveBudget(Long evenementId, String montantTotal) {
        return budgetRepository.saveAndFlush(
                new Budget(evenementId, new BigDecimal(montantTotal)));
    }

    private void saveExpense(Budget budget, String description, String montant) {
        depenseRepository.saveAndFlush(new Depense(
                budget,
                description,
                new BigDecimal(montant),
                "AUTRE",
                LocalDate.of(2026, 11, 20),
                null));
    }
}
