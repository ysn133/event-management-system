package com.eventflow.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.hasItems;
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
class DepenseListingIntegrationTests {

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
    void returnsOnlyExpensesForRequestedBudget() throws Exception {
        Budget requestedBudget = budgetRepository.saveAndFlush(
                new Budget(101L, new BigDecimal("10000.00")));
        Budget otherBudget = budgetRepository.saveAndFlush(
                new Budget(102L, new BigDecimal("8000.00")));

        Depense first = depenseRepository.saveAndFlush(new Depense(
                requestedBudget,
                "Venue",
                new BigDecimal("2500.00"),
                "SALLE",
                LocalDate.of(2026, 11, 20),
                "PAYEE"));
        Depense second = depenseRepository.saveAndFlush(new Depense(
                requestedBudget,
                "Catering",
                new BigDecimal("1750.50"),
                "TRAITEUR",
                LocalDate.of(2026, 11, 21),
                null));
        depenseRepository.saveAndFlush(new Depense(
                otherBudget,
                "Other event expense",
                new BigDecimal("999.00"),
                "AUTRE",
                LocalDate.of(2026, 11, 22),
                null));

        mockMvc.perform(get("/api/budgets/{id}/depenses", requestedBudget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].budgetId").value(requestedBudget.getId()))
                .andExpect(jsonPath("$[0].description").value("Venue"))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[1].budgetId").value(requestedBudget.getId()))
                .andExpect(jsonPath("$[1].description").value("Catering"));
    }

    @Test
    void returnsEmptyArrayWhenBudgetHasNoExpenses() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(103L, new BigDecimal("5000.00")));

        mockMvc.perform(get("/api/budgets/{id}/depenses", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsNotFoundWhenListingMissingBudget() throws Exception {
        mockMvc.perform(get("/api/budgets/{id}/depenses", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void filtersExpensesByCategoryAndStatus() throws Exception {
        Budget budget = saveFilterBudget();
        saveExpense(budget, "Paid venue", "2500.00", "SALLE", "2026-11-20", "PAYEE");
        saveExpense(budget, "Planned venue", "1000.00", "SALLE", "2026-11-21", "PREVUE");
        saveExpense(budget, "Paid catering", "1750.00", "TRAITEUR", "2026-11-22", "PAYEE");

        mockMvc.perform(get("/api/budgets/{id}/depenses", budget.getId())
                        .param("categorie", "salle")
                        .param("statut", "PAYEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Paid venue"));
    }

    @Test
    void filtersExpensesByDateAndAmountRanges() throws Exception {
        Budget budget = saveFilterBudget();
        saveExpense(budget, "Early", "500.00", "AUTRE", "2026-11-10", "PREVUE");
        saveExpense(budget, "Matching", "1500.00", "AUTRE", "2026-11-20", "EN_ATTENTE");
        saveExpense(budget, "Late", "3000.00", "AUTRE", "2026-11-30", "PAYEE");

        mockMvc.perform(get("/api/budgets/{id}/depenses", budget.getId())
                        .param("dateDebut", "2026-11-15")
                        .param("dateFin", "2026-11-25")
                        .param("montantMin", "1000.00")
                        .param("montantMax", "2000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Matching"));
    }

    @Test
    void rejectsInvalidExpenseFilters() throws Exception {
        Budget budget = saveFilterBudget();

        mockMvc.perform(get("/api/budgets/{id}/depenses", budget.getId())
                        .param("statut", "A_PAYER")
                        .param("montantMin", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("statut", "montantMin")));
    }

    private Budget saveFilterBudget() {
        return budgetRepository.saveAndFlush(
                new Budget(104L, new BigDecimal("10000.00")));
    }

    private void saveExpense(
            Budget budget,
            String description,
            String amount,
            String category,
            String date,
            String status) {
        depenseRepository.saveAndFlush(new Depense(
                budget,
                description,
                new BigDecimal(amount),
                category,
                LocalDate.parse(date),
                status));
    }
}
