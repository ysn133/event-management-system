package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class DepenseCreationIntegrationTests {

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
    void createsExpenseEvenWhenItExceedsBudget() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(91L, new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/budgets/{id}/depenses", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Venue and equipment",
                                  "montant": 1500.00,
                                  "categorie": "SALLE",
                                  "dateDepense": "2026-11-20",
                                  "statut": "PAYEE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        matchesPattern("http://localhost/api/depenses/[1-9][0-9]*")))
                .andExpect(jsonPath("$.budgetId").value(budget.getId()))
                .andExpect(jsonPath("$.description").value("Venue and equipment"))
                .andExpect(jsonPath("$.montant").value(1500.00))
                .andExpect(jsonPath("$.categorie").value("SALLE"))
                .andExpect(jsonPath("$.dateDepense").value("2026-11-20"))
                .andExpect(jsonPath("$.statut").value("PAYEE"));

        Depense savedDepense = depenseRepository.findAll().getFirst();
        assertThat(savedDepense.getBudget().getId()).isEqualTo(budget.getId());
        assertThat(savedDepense.getMontant())
                .isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(savedDepense.getDateDepense()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void defaultsExpenseStatusToPlannedWhenOmitted() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(92L, new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/budgets/{id}/depenses", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Catering",
                                  "montant": 750.00,
                                  "categorie": "TRAITEUR",
                                  "dateDepense": "2026-11-20"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PREVUE"));

        assertThat(depenseRepository.findAll().getFirst().getStatut()).isEqualTo("PREVUE");
    }

    @Test
    void rejectsUnknownExpenseStatus() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(93L, new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/budgets/{id}/depenses", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Catering",
                                  "montant": 750.00,
                                  "categorie": "TRAITEUR",
                                  "dateDepense": "2026-11-20",
                                  "statut": "A_PAYER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("statut"));

        assertThat(depenseRepository.count()).isZero();
    }

    @Test
    void rejectsInvalidExpenseData() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
                new Budget(91L, new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/budgets/{id}/depenses", budget.getId())
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
    void returnsNotFoundWhenBudgetDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/budgets/{id}/depenses", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Catering",
                                  "montant": 750.00,
                                  "categorie": "TRAITEUR",
                                  "dateDepense": "2026-11-20"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
