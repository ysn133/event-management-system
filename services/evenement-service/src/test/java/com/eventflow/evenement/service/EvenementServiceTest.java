package com.eventflow.evenement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.eventflow.evenement.domain.Evenement;
import com.eventflow.evenement.dto.EvenementCreateRequest;
import com.eventflow.evenement.dto.EvenementResponse;
import com.eventflow.evenement.dto.EvenementUpdateRequest;
import com.eventflow.evenement.dto.EvenementStatusUpdateRequest;
import com.eventflow.evenement.domain.EvenementStatut;
import com.eventflow.evenement.exception.EvenementInUseException;
import com.eventflow.evenement.exception.EvenementNotFoundException;
import com.eventflow.evenement.exception.InvalidEventPeriodException;
import com.eventflow.evenement.exception.InvalidStatusTransitionException;
import com.eventflow.evenement.exception.OrganisateurNotFoundException;
import com.eventflow.evenement.integration.OrganisateurValidationPort;
import com.eventflow.evenement.repository.EvenementRepository;

@ExtendWith(MockitoExtension.class)
class EvenementServiceTest {

    private static final Long EVENT_ID = 10L;
    private static final Long ORGANIZER_ID = 20L;
    private static final OffsetDateTime START = OffsetDateTime.of(
            2026, 12, 10, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END = START.plusHours(8);

    @Mock
    private EvenementRepository evenementRepository;

    @Mock
    private OrganisateurValidationPort organisateurValidationPort;

    @InjectMocks
    private EvenementService evenementService;

    @Test
    void createsEventWithDefaultStatus() {
        EvenementCreateRequest request = createRequest(ORGANIZER_ID, START, END, null);
        when(organisateurValidationPort.existsById(ORGANIZER_ID)).thenReturn(true);
        when(evenementRepository.saveAndFlush(any(Evenement.class))).thenAnswer(invocation -> {
            Evenement savedEvent = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedEvent, "id", EVENT_ID);
            return savedEvent;
        });

        EvenementResponse response = evenementService.create(request);

        assertThat(response.id()).isEqualTo(EVENT_ID);
        assertThat(response.organisateurId()).isEqualTo(ORGANIZER_ID);
        assertThat(response.titre()).isEqualTo("Web Services Workshop");
        assertThat(response.statut()).isEqualTo("PLANIFIE");

        ArgumentCaptor<Evenement> captor = ArgumentCaptor.forClass(Evenement.class);
        verify(evenementRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo("PLANIFIE");
    }

    @Test
    void rejectsCreationWhenOrganizerDoesNotExist() {
        EvenementCreateRequest request = createRequest(ORGANIZER_ID, START, END, "CONFIRME");
        when(organisateurValidationPort.existsById(ORGANIZER_ID)).thenReturn(false);

        assertThatThrownBy(() -> evenementService.create(request))
                .isInstanceOf(OrganisateurNotFoundException.class);

        verifyNoInteractions(evenementRepository);
    }

    @Test
    void rejectsCreationWhenPeriodIsInvalid() {
        EvenementCreateRequest request = createRequest(ORGANIZER_ID, START, START, "CONFIRME");
        when(organisateurValidationPort.existsById(ORGANIZER_ID)).thenReturn(true);

        assertThatThrownBy(() -> evenementService.create(request))
                .isInstanceOf(InvalidEventPeriodException.class);

        verify(evenementRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsExistingEventById() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "CONFIRME");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        EvenementResponse response = evenementService.getById(EVENT_ID);

        assertThat(response.id()).isEqualTo(EVENT_ID);
        assertThat(response.organisateurId()).isEqualTo(ORGANIZER_ID);
        assertThat(response.titre()).isEqualTo("Workshop");
        assertThat(response.statut()).isEqualTo("CONFIRME");
    }

    @Test
    void rejectsRetrievalWhenEventDoesNotExist() {
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evenementService.getById(EVENT_ID))
                .isInstanceOf(EvenementNotFoundException.class);
    }

    @Test
    void listsAllEventsWhenOrganizerFilterIsAbsent() {
        Evenement first = event(10L, 20L, "Workshop", "PLANIFIE");
        Evenement second = event(11L, 21L, "Conference", "CONFIRME");
        when(evenementRepository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));

        List<EvenementResponse> responses = evenementService.list(null);

        assertThat(responses).extracting(EvenementResponse::id).containsExactly(10L, 11L);
        assertThat(responses).extracting(EvenementResponse::titre)
                .containsExactly("Workshop", "Conference");
        verify(evenementRepository, never()).findAllByOrganisateurIdOrderByIdAsc(any());
    }

    @Test
    void listsOnlyEventsForRequestedOrganizer() {
        Evenement first = event(10L, ORGANIZER_ID, "Workshop", "PLANIFIE");
        Evenement second = event(12L, ORGANIZER_ID, "Conference", "CONFIRME");
        when(evenementRepository.findAllByOrganisateurIdOrderByIdAsc(ORGANIZER_ID))
                .thenReturn(List.of(first, second));

        List<EvenementResponse> responses = evenementService.list(ORGANIZER_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(response -> response.organisateurId().equals(ORGANIZER_ID));
        assertThat(responses).extracting(EvenementResponse::id).containsExactly(10L, 12L);
        verify(evenementRepository, never()).findAllByOrderByIdAsc();
    }

    @Test
    void updatesExistingEvent() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Old title", "PLANIFIE");
        EvenementUpdateRequest request = new EvenementUpdateRequest(
                "Updated title",
                "Updated description",
                START.plusDays(1),
                END.plusDays(1),
                "Room B",
                "CONFIRME");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(evenementRepository.saveAndFlush(event)).thenReturn(event);

        EvenementResponse response = evenementService.update(EVENT_ID, request);

        assertThat(response.id()).isEqualTo(EVENT_ID);
        assertThat(response.organisateurId()).isEqualTo(ORGANIZER_ID);
        assertThat(response.titre()).isEqualTo("Updated title");
        assertThat(response.description()).isEqualTo("Updated description");
        assertThat(response.dateDebut()).isEqualTo(START.plusDays(1));
        assertThat(response.dateFin()).isEqualTo(END.plusDays(1));
        assertThat(response.lieu()).isEqualTo("Room B");
        assertThat(response.statut()).isEqualTo("CONFIRME");
        verify(evenementRepository).saveAndFlush(event);
    }

    @Test
    void rejectsSkippedStatusTransition() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "PLANIFIE");
        EvenementUpdateRequest request = new EvenementUpdateRequest(
                "Workshop", "Description", START, END, "Room A", "TERMINE");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> evenementService.update(EVENT_ID, request))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(evenementRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsModificationOfTerminalEvent() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "TERMINE");
        EvenementUpdateRequest request = new EvenementUpdateRequest(
                "Changed title", "Description", START, END, "Room A", "TERMINE");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> evenementService.update(EVENT_ID, request))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updatesEventStatusThroughDedicatedOperation() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "CONFIRME");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(evenementRepository.saveAndFlush(event)).thenReturn(event);

        EvenementResponse response = evenementService.updateStatus(
                EVENT_ID, new EvenementStatusUpdateRequest(EvenementStatut.EN_COURS));

        assertThat(response.statut()).isEqualTo("EN_COURS");
    }

    @Test
    void rejectsInvalidDedicatedStatusTransition() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "PLANIFIE");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> evenementService.updateStatus(
                EVENT_ID, new EvenementStatusUpdateRequest(EvenementStatut.TERMINE)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void rejectsUpdateWhenEventDoesNotExist() {
        EvenementUpdateRequest request = updateRequest(START, END);
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evenementService.update(EVENT_ID, request))
                .isInstanceOf(EvenementNotFoundException.class);

        verify(evenementRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUpdateBeforeLookupWhenPeriodIsInvalid() {
        EvenementUpdateRequest request = updateRequest(START, START.minusMinutes(1));

        assertThatThrownBy(() -> evenementService.update(EVENT_ID, request))
                .isInstanceOf(InvalidEventPeriodException.class);

        verifyNoInteractions(evenementRepository);
    }

    @Test
    void deletesExistingEventAndFlushesRepository() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "PLANIFIE");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        evenementService.delete(EVENT_ID);

        verify(evenementRepository).delete(event);
        verify(evenementRepository).flush();
    }

    @Test
    void rejectsDeletionWhenEventDoesNotExist() {
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evenementService.delete(EVENT_ID))
                .isInstanceOf(EvenementNotFoundException.class);

        verify(evenementRepository, never()).delete(any(Evenement.class));
        verify(evenementRepository, never()).flush();
    }

    @Test
    void translatesPersistenceConflictWhenEventIsInUse() {
        Evenement event = event(EVENT_ID, ORGANIZER_ID, "Workshop", "PLANIFIE");
        when(evenementRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        doThrow(new DataIntegrityViolationException("referenced event"))
                .when(evenementRepository).flush();

        assertThatThrownBy(() -> evenementService.delete(EVENT_ID))
                .isInstanceOf(EvenementInUseException.class);

        verify(evenementRepository).delete(event);
        verify(evenementRepository).flush();
    }

    private EvenementCreateRequest createRequest(
            Long organizerId,
            OffsetDateTime start,
            OffsetDateTime end,
            String status) {
        return new EvenementCreateRequest(
                organizerId,
                "Web Services Workshop",
                "Team workshop",
                start,
                end,
                "Room A",
                status);
    }

    private EvenementUpdateRequest updateRequest(OffsetDateTime start, OffsetDateTime end) {
        return new EvenementUpdateRequest(
                "Updated event",
                "Updated description",
                start,
                end,
                "Room B",
                "CONFIRME");
    }

    private Evenement event(Long id, Long organizerId, String title, String status) {
        Evenement event = new Evenement(
                organizerId,
                title,
                "Description",
                START,
                END,
                "Room A",
                status);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
