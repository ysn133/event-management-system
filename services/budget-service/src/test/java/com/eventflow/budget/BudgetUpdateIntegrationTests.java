package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser
@SpringBootTest
class BudgetUpdateIntegrationTests {

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
    void cleanUp() { depenseRepository.deleteAll(); budgetRepository.deleteAll(); }

    private Budget budgetWithCommitments(String amount, boolean overspending) {
        Budget budget = budgetRepository.saveAndFlush(new Budget(73L, new BigDecimal(amount), overspending));
        // Only pending and paid expenses contribute to the committed total: 8,000.
        for (String[] entry : new String[][] {{"3000", "PAYEE"}, {"5000", "EN_ATTENTE"}, {"6000", "PREVUE"}, {"2000", "ANNULEE"}}) {
            depenseRepository.saveAndFlush(new Depense(budget, "Expense " + entry[1], new BigDecimal(entry[0]), "Autres", java.time.LocalDate.of(2027, 1, 10), entry[1]));
        }
        return budget;
    }

    @Test
    void rejectsReductionBelowCommitmentsWithoutChangingBudget() throws Exception {
        Budget budget = budgetWithCommitments("10000", false);
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":7000,\"depassementAutorise\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUDGET_EXCEEDED"));
        Budget stored = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(stored.getMontantTotal()).isEqualByComparingTo("10000");
        assertThat(stored.isDepassementAutorise()).isFalse();
    }

    @Test
    void omittedOverspendingSettingStillEnforcesCurrentRestriction() throws Exception {
        Budget budget = budgetWithCommitments("10000", false);
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":7000}"))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptsExactCommittedAmountAndExcludesPlannedAndCancelledExpenses() throws Exception {
        Budget budget = budgetWithCommitments("10000", false);
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":8000,\"depassementAutorise\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantTotal").value(8000));
    }

    @Test
    void acceptsReductionBelowCommitmentsWhenOverspendingIsExplicitlyAllowed() throws Exception {
        Budget budget = budgetWithCommitments("10000", false);
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":6000,\"depassementAutorise\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantTotal").value(6000))
                .andExpect(jsonPath("$.depassementAutorise").value(true));
    }

    @Test
    void cannotDisableOverspendingWhileExistingCommitmentsExceedAllocation() throws Exception {
        Budget budget = budgetWithCommitments("6000", true);
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":6000,\"depassementAutorise\":false}"))
                .andExpect(status().isConflict());
        assertThat(budgetRepository.findById(budget.getId()).orElseThrow().isDepassementAutorise()).isTrue();
    }

    @Test
    void canDisableOverspendingWhileRaisingAllocationToCoverCommitments() throws Exception {
        Budget budget = budgetWithCommitments("6000", true);
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":9000,\"depassementAutorise\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantTotal").value(9000))
                .andExpect(jsonPath("$.depassementAutorise").value(false));
    }

    @Test
    void canReduceAnUnusedBudget() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(new Budget(73L, new BigDecimal("10000"), false));
        mockMvc.perform(put("/api/budgets/{id}", budget.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantTotal\":1,\"depassementAutorise\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantTotal").value(1));
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
