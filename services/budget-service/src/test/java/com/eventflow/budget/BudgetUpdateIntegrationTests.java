package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
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

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BudgetUpdateIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @BeforeEach
    void setUp() {
        budgetRepository.deleteAll();
    }

    @Test
    void updatesOnlyTheBudgetAmount() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(73L, new BigDecimal("20000.00")));

        mockMvc.perform(put("/api/budgets/{id}", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "montantTotal": 27500.50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(budget.getId()))
                .andExpect(jsonPath("$.evenementId").value(73))
                .andExpect(jsonPath("$.montantTotal").value(27500.50));

        Budget updatedBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(updatedBudget.getEvenementId()).isEqualTo(73L);
        assertThat(updatedBudget.getMontantTotal())
                .isEqualByComparingTo(new BigDecimal("27500.50"));
    }

    @Test
    void rejectsInvalidBudgetAmount() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(73L, new BigDecimal("20000.00")));

        mockMvc.perform(put("/api/budgets/{id}", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "montantTotal": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("montantTotal"));
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingBudget() throws Exception {
        mockMvc.perform(put("/api/budgets/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "montantTotal": 5000.00
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
