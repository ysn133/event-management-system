package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@SpringBootTest
class DepenseStatusUpdateIntegrationTests {

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

    @ParameterizedTest
    @ValueSource(strings = {"PREVUE", "EN_ATTENTE", "PAYEE", "ANNULEE"})
    void updatesExpenseToEverySupportedStatus(String statut) throws Exception {
        Depense depense = saveExpense();

        mockMvc.perform(patch("/api/depenses/{id}/statut", depense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut": "%s"}
                                """.formatted(statut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(depense.getId()))
                .andExpect(jsonPath("$.statut").value(statut));

        assertThat(depenseRepository.findById(depense.getId()).orElseThrow().getStatut())
                .isEqualTo(statut);
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        Depense depense = saveExpense();

        mockMvc.perform(patch("/api/depenses/{id}/statut", depense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\": \"A_PAYER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("statut"));

        assertThat(depenseRepository.findById(depense.getId()).orElseThrow().getStatut())
                .isEqualTo("PREVUE");
    }

    @Test
    void rejectsMissingStatus() throws Exception {
        Depense depense = saveExpense();

        mockMvc.perform(patch("/api/depenses/{id}/statut", depense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("statut"));
    }

    @Test
    void returnsNotFoundWhenExpenseDoesNotExist() throws Exception {
        mockMvc.perform(patch("/api/depenses/{id}/statut", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\": \"PAYEE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Depense saveExpense() {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(141L, new BigDecimal("10000.00")));
        return depenseRepository.saveAndFlush(new Depense(
                budget,
                "Venue",
                new BigDecimal("2500.00"),
                "SALLE",
                LocalDate.of(2026, 11, 20),
                "PREVUE"));
    }
}
