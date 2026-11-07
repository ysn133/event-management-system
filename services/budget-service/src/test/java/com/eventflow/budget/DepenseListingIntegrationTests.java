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
}
