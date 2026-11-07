package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class DepenseDeletionIntegrationTests {

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
    void deletesExpenseAndRemovesItFromBudgetListing() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(121L, new BigDecimal("10000.00")));
        Depense depense = depenseRepository.saveAndFlush(new Depense(
                budget,
                "Venue",
                new BigDecimal("2500.00"),
                "SALLE",
                LocalDate.of(2026, 11, 20),
                "PAYEE"));

        mockMvc.perform(delete("/api/depenses/{id}", depense.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(depenseRepository.existsById(depense.getId())).isFalse();
        mockMvc.perform(get("/api/budgets/{id}/depenses", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsNotFoundWhenDeletingMissingExpense() throws Exception {
        mockMvc.perform(delete("/api/depenses/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
