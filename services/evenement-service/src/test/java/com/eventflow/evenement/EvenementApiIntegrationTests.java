package com.eventflow.evenement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventflow.evenement.domain.Evenement;
import com.eventflow.evenement.repository.EvenementRepository;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class EvenementApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EvenementRepository evenementRepository;

    @BeforeEach
    void setUp() {
        evenementRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        evenementRepository.deleteAll();
    }

    @Test
    void createsEventWithDefaultStatus() throws Exception {
        mockMvc.perform(post("/api/evenements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organisateurId": 21,
                                  "titre": "Architecture conference",
                                  "description": "Annual team conference",
                                  "dateDebut": "2026-12-10T09:00:00Z",
                                  "dateFin": "2026-12-10T18:00:00Z",
                                  "lieu": "Casablanca"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.organisateurId").value(21))
                .andExpect(jsonPath("$.titre").value("Architecture conference"))
                .andExpect(jsonPath("$.dateDebut").value("2026-12-10T09:00:00Z"))
                .andExpect(jsonPath("$.dateFin").value("2026-12-10T18:00:00Z"))
                .andExpect(jsonPath("$.statut").value("PLANIFIE"));

        assertThat(evenementRepository.findAll()).hasSize(1);
    }

    @Test
    void getsAndFiltersEventsByOrganizer() throws Exception {
        Evenement first = saveEvent(31L, "First event");
        saveEvent(31L, "Second event");
        saveEvent(32L, "Other organizer event");

        mockMvc.perform(get("/api/evenements/{id}", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.titre").value("First event"));

        mockMvc.perform(get("/api/evenements")
                        .queryParam("organisateurId", "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].titre", hasItems("First event", "Second event")));

        mockMvc.perform(get("/api/evenements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void updatesEditableFieldsWithoutChangingOrganizer() throws Exception {
        Evenement evenement = saveEvent(41L, "Initial title");

        mockMvc.perform(put("/api/evenements/{id}", evenement.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titre": "Updated title",
                                  "description": "Updated description",
                                  "dateDebut": "2026-12-11T10:00:00+01:00",
                                  "dateFin": "2026-12-11T20:00:00+01:00",
                                  "lieu": "Rabat",
                                  "statut": "CONFIRME"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evenement.getId()))
                .andExpect(jsonPath("$.organisateurId").value(41))
                .andExpect(jsonPath("$.titre").value("Updated title"))
                .andExpect(jsonPath("$.lieu").value("Rabat"))
                .andExpect(jsonPath("$.statut").value("CONFIRME"));

        Evenement updated = evenementRepository.findById(evenement.getId()).orElseThrow();
        assertThat(updated.getOrganisateurId()).isEqualTo(41L);
        assertThat(updated.getTitre()).isEqualTo("Updated title");
    }

    @Test
    void rejectsInvalidDataAndInvalidDatePeriod() throws Exception {
        mockMvc.perform(post("/api/evenements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organisateurId": 0,
                                  "titre": " ",
                                  "dateFin": "2026-12-10T18:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath(
                        "$.fieldErrors[*].field",
                        hasItems("organisateurId", "titre", "dateDebut")));

        mockMvc.perform(post("/api/evenements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organisateurId": 51,
                                  "titre": "Invalid period",
                                  "dateDebut": "2026-12-10T18:00:00Z",
                                  "dateFin": "2026-12-10T09:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_PERIOD"));
    }

    @Test
    void returnsNotFoundForMissingEvent() throws Exception {
        mockMvc.perform(get("/api/evenements/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/evenements/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titre": "Missing event",
                                  "dateDebut": "2026-12-10T09:00:00Z",
                                  "dateFin": "2026-12-10T18:00:00Z",
                                  "statut": "PLANIFIE"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/evenements/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deletesExistingEvent() throws Exception {
        Evenement evenement = saveEvent(61L, "Event to delete");

        mockMvc.perform(delete("/api/evenements/{id}", evenement.getId()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(evenementRepository.existsById(evenement.getId())).isFalse();
    }

    @Test
    void allowsRequestsFromConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/evenements")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        "GET,POST,PUT,DELETE,OPTIONS"));
    }

    private Evenement saveEvent(Long organisateurId, String titre) {
        return evenementRepository.saveAndFlush(new Evenement(
                organisateurId,
                titre,
                null,
                OffsetDateTime.parse("2026-12-10T09:00:00Z"),
                OffsetDateTime.parse("2026-12-10T18:00:00Z"),
                "Casablanca",
                "PLANIFIE"));
    }
}
