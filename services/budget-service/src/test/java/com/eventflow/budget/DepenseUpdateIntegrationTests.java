package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class DepenseUpdateIntegrationTests {

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
    void updatesEditableFieldsWithoutChangingBudgetAssociation() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(111L, new BigDecimal("10000.00")));
        Depense depense = depenseRepository.saveAndFlush(new Depense(
                budget,
                "Venue",
                new BigDecimal("2500.00"),
                "SALLE",
                LocalDate.of(2026, 11, 20),
                "EN_ATTENTE"));

        mockMvc.perform(put("/api/depenses/{id}", depense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Venue and equipment",
                                  "montant": 12500.50,
                                  "categorie": "LOGISTIQUE",
                                  "dateDepense": "2026-11-22",
                                  "statut": "PAYEE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(depense.getId()))
                .andExpect(jsonPath("$.budgetId").value(budget.getId()))
                .andExpect(jsonPath("$.description").value("Venue and equipment"))
                .andExpect(jsonPath("$.montant").value(12500.50))
                .andExpect(jsonPath("$.categorie").value("LOGISTIQUE"))
                .andExpect(jsonPath("$.dateDepense").value("2026-11-22"))
                .andExpect(jsonPath("$.statut").value("PAYEE"));

        Depense updatedDepense = depenseRepository.findById(depense.getId()).orElseThrow();
        assertThat(updatedDepense.getBudget().getId()).isEqualTo(budget.getId());
        assertThat(updatedDepense.getMontant())
                .isEqualByComparingTo(new BigDecimal("12500.50"));
        assertThat(updatedDepense.getDateDepense()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void rejectsInvalidEditableFields() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(112L, new BigDecimal("10000.00")));
        Depense depense = depenseRepository.saveAndFlush(new Depense(
                budget,
                "Catering",
                new BigDecimal("1500.00"),
                "TRAITEUR",
                LocalDate.of(2026, 11, 20),
                "PREVUE"));

        mockMvc.perform(put("/api/depenses/{id}", depense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": " ",
                                  "montant": 0,
                                  "categorie": "",
                                  "statut": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath(
                        "$.fieldErrors[*].field",
                        hasItems("description", "montant", "categorie", "dateDepense", "statut")));
    }

    @Test
    void rejectsUnknownExpenseStatus() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(113L, new BigDecimal("10000.00")));
        Depense depense = depenseRepository.saveAndFlush(new Depense(
                budget,
                "Catering",
                new BigDecimal("1500.00"),
                "TRAITEUR",
                LocalDate.of(2026, 11, 20),
                "PREVUE"));

        mockMvc.perform(put("/api/depenses/{id}", depense.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Catering",
                                  "montant": 1750.00,
                                  "categorie": "TRAITEUR",
                                  "dateDepense": "2026-11-21",
                                  "statut": "A_PAYER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("statut"));

        Depense unchanged = depenseRepository.findById(depense.getId()).orElseThrow();
        assertThat(unchanged.getStatut()).isEqualTo("PREVUE");
        assertThat(unchanged.getMontant()).isEqualByComparingTo("1500.00");
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingExpense() throws Exception {
        mockMvc.perform(put("/api/depenses/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Catering",
                                  "montant": 750.00,
                                  "categorie": "TRAITEUR",
                                  "dateDepense": "2026-11-20",
                                  "statut": "PREVUE"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
