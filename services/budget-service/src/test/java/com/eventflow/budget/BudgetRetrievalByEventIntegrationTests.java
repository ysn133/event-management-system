package com.eventflow.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.repository.BudgetRepository;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BudgetRetrievalByEventIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @BeforeEach
    void setUp() {
        budgetRepository.deleteAll();
    }

    @Test
    void returnsBudgetByEventId() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(42L, new BigDecimal("8750.25")));

        mockMvc.perform(get("/api/budgets/evenement/{evenementId}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(budget.getId()))
                .andExpect(jsonPath("$.evenementId").value(42))
                .andExpect(jsonPath("$.montantTotal").value(8750.25));
    }

    @Test
    void returnsNotFoundWhenEventHasNoBudget() throws Exception {
        mockMvc.perform(get("/api/budgets/evenement/{evenementId}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Budget not found"));
    }
}
