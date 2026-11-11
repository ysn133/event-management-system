package com.eventflow.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BudgetClosureIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private DepenseRepository depenseRepository;

    @BeforeEach
    void setUp() {
        depenseRepository.deleteAll();
        budgetRepository.deleteAll();
    }

    @Test
    void closesAndReopensBudget() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(new Budget(501L, new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/budgets/{id}/cloturer", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOTURE"));

        mockMvc.perform(post("/api/budgets/{id}/rouvrir", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("OUVERT"));
    }

    @Test
    void closedBudgetIsReadOnlyForBudgetAndExpenses() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(new Budget(502L, new BigDecimal("1000.00")));
        mockMvc.perform(post("/api/budgets/{id}/cloturer", budget.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/budgets/{id}", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":2000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUDGET_CLOSED"));

        mockMvc.perform(post("/api/budgets/{id}/depenses", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description":"Venue",
                                  "montant":500,
                                  "categorie":"SALLE",
                                  "dateDepense":"2026-11-11"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUDGET_CLOSED"));
    }

    @Test
    void rejectsRepeatedStatusOperations() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(new Budget(503L, new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/budgets/{id}/rouvrir", budget.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BUDGET_STATUS_TRANSITION"));

        mockMvc.perform(post("/api/budgets/{id}/cloturer", budget.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/budgets/{id}/cloturer", budget.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BUDGET_STATUS_TRANSITION"));
    }
}
