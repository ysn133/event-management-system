package com.eventflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventflow.budget.integration.EventValidationPort;
import com.eventflow.budget.repository.BudgetRepository;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import(BudgetCreationIntegrationTests.EventValidationTestConfiguration.class)
class BudgetCreationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private StubEventValidationPort eventValidationPort;

    @BeforeEach
    void setUp() {
        budgetRepository.deleteAll();
        eventValidationPort.setEventExists(true);
    }

    @Test
    void createsBudgetForExistingEvent() throws Exception {
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evenementId": 15,
                                  "montantTotal": 50000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        matchesPattern("http://localhost/api/budgets/[1-9][0-9]*")))
                .andExpect(jsonPath("$.id").value(greaterThan(0)))
                .andExpect(jsonPath("$.evenementId").value(15))
                .andExpect(jsonPath("$.montantTotal").value(50000.00));

        assertThat(budgetRepository.findAll())
                .singleElement()
                .satisfies(budget -> {
                    assertThat(budget.getEvenementId()).isEqualTo(15L);
                    assertThat(budget.getMontantTotal())
                            .isEqualByComparingTo(new BigDecimal("50000.00"));
                });
    }

    @Test
    void rejectsInvalidBudgetData() throws Exception {
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evenementId": 0,
                                  "montantTotal": -10.001
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("evenementId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("montantTotal")));
    }

    @Test
    void returnsNotFoundWhenEventDoesNotExist() throws Exception {
        eventValidationPort.setEventExists(false);

        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evenementId": 99,
                                  "montantTotal": 1000.00
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenEventAlreadyHasBudget() throws Exception {
        String requestBody = """
                {
                  "evenementId": 15,
                  "montantTotal": 50000.00
                }
                """;

        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BUDGET_ALREADY_EXISTS"));
    }

    @TestConfiguration
    static class EventValidationTestConfiguration {

        @Bean
        @Primary
        StubEventValidationPort stubEventValidationPort() {
            return new StubEventValidationPort();
        }
    }

    static class StubEventValidationPort implements EventValidationPort {

        private boolean eventExists;

        void setEventExists(boolean eventExists) {
            this.eventExists = eventExists;
        }

        @Override
        public boolean existsById(Long eventId) {
            return eventExists;
        }
    }
}
