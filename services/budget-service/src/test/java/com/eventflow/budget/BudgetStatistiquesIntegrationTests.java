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
class BudgetStatistiquesIntegrationTests {

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
    void calculatesStatisticsAndExcludesCancelledExpenses() throws Exception {
        Budget budget = saveBudget(151L, "10000.00");
        saveExpense(budget, "Planned venue", "1000.00", "SALLE", "PREVUE");
        saveExpense(budget, "Paid venue", "2000.00", "SALLE", "PAYEE");
        Depense largest = saveExpense(
                budget,
                "Pending catering",
                "3000.00",
                "TRAITEUR",
                "EN_ATTENTE");
        saveExpense(budget, "Cancelled transport", "9000.00", "TRANSPORT", "ANNULEE");

        mockMvc.perform(get("/api/budgets/{id}/statistiques", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId").value(budget.getId()))
                .andExpect(jsonPath("$.evenementId").value(151))
                .andExpect(jsonPath("$.nombreDepenses").value(3))
                .andExpect(jsonPath("$.totalDepenses").value(6000.00))
                .andExpect(jsonPath("$.montantMoyen").value(2000.00))
                .andExpect(jsonPath("$.depenseLaPlusElevee.id").value(largest.getId()))
                .andExpect(jsonPath("$.depenseLaPlusElevee.description")
                        .value("Pending catering"))
                .andExpect(jsonPath("$.depenseLaPlusElevee.montant").value(3000.00))
                .andExpect(jsonPath("$.repartitionParCategorie.length()").value(2))
                .andExpect(jsonPath("$.repartitionParCategorie[0].categorie").value("SALLE"))
                .andExpect(jsonPath("$.repartitionParCategorie[0].montantTotal").value(3000.00))
                .andExpect(jsonPath("$.repartitionParCategorie[0].pourcentage").value(50.00))
                .andExpect(jsonPath("$.repartitionParCategorie[1].categorie").value("TRAITEUR"))
                .andExpect(jsonPath("$.repartitionParCategorie[1].pourcentage").value(50.00))
                .andExpect(jsonPath("$.pourcentageConsommation").value(50.00))
                .andExpect(jsonPath("$.niveauAlerte").value("NORMAL"));
    }

    @Test
    void returnsEmptyStatisticsWhenBudgetHasNoExpenses() throws Exception {
        Budget budget = saveBudget(152L, "10000.00");

        mockMvc.perform(get("/api/budgets/{id}/statistiques", budget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreDepenses").value(0))
                .andExpect(jsonPath("$.totalDepenses").value(0))
                .andExpect(jsonPath("$.montantMoyen").value(0))
                .andExpect(jsonPath("$.depenseLaPlusElevee").isEmpty())
                .andExpect(jsonPath("$.repartitionParCategorie").isEmpty())
                .andExpect(jsonPath("$.pourcentageConsommation").value(0))
                .andExpect(jsonPath("$.niveauAlerte").value("NORMAL"));
    }

    @Test
    void returnsNotFoundWhenBudgetDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/budgets/{id}/statistiques", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Budget saveBudget(Long evenementId, String montantTotal) {
        return budgetRepository.saveAndFlush(
                new Budget(evenementId, new BigDecimal(montantTotal)));
    }

    private Depense saveExpense(
            Budget budget,
            String description,
            String montant,
            String categorie,
            String statut) {
        return depenseRepository.saveAndFlush(new Depense(
                budget,
                description,
                new BigDecimal(montant),
                categorie,
                LocalDate.of(2026, 11, 20),
                statut));
    }
}
