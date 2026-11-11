package com.eventflow.evenement.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.evenement.domain.Evenement;
import com.eventflow.evenement.domain.EvenementStatut;
import com.eventflow.evenement.dto.EvenementCreateRequest;
import com.eventflow.evenement.dto.EvenementResponse;
import com.eventflow.evenement.dto.EvenementUpdateRequest;
import com.eventflow.evenement.dto.EvenementStatusUpdateRequest;
import com.eventflow.evenement.exception.EvenementInUseException;
import com.eventflow.evenement.exception.EvenementNotFoundException;
import com.eventflow.evenement.exception.InvalidEventPeriodException;
import com.eventflow.evenement.exception.InvalidStatusTransitionException;
import com.eventflow.evenement.exception.OrganisateurNotFoundException;
import com.eventflow.evenement.integration.OrganisateurValidationPort;
import com.eventflow.evenement.repository.EvenementRepository;

@Service
public class EvenementService {

    private static final String DEFAULT_STATUS = "PLANIFIE";

    private final EvenementRepository evenementRepository;
    private final OrganisateurValidationPort organisateurValidationPort;

    public EvenementService(
            EvenementRepository evenementRepository,
            OrganisateurValidationPort organisateurValidationPort) {
        this.evenementRepository = evenementRepository;
        this.organisateurValidationPort = organisateurValidationPort;
    }

    @Transactional
    public EvenementResponse create(EvenementCreateRequest request) {
        if (!organisateurValidationPort.existsById(request.organisateurId())) {
            throw new OrganisateurNotFoundException(request.organisateurId());
        }
        validatePeriod(request.dateDebut(), request.dateFin());

        Evenement evenement = new Evenement(
                request.organisateurId(),
                request.titre(),
                request.description(),
                request.dateDebut(),
                request.dateFin(),
                request.lieu(),
                request.statut() == null ? DEFAULT_STATUS : request.statut());

        try {
            return EvenementResponse.from(evenementRepository.saveAndFlush(evenement));
        } catch (DataIntegrityViolationException exception) {
            throw new OrganisateurNotFoundException(request.organisateurId());
        }
    }

    @Transactional(readOnly = true)
    public EvenementResponse getById(Long id) {
        return evenementRepository.findById(id)
                .map(EvenementResponse::from)
                .orElseThrow(() -> new EvenementNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<EvenementResponse> list(Long organisateurId) {
        List<Evenement> evenements = organisateurId == null
                ? evenementRepository.findAllByOrderByIdAsc()
                : evenementRepository.findAllByOrganisateurIdOrderByIdAsc(organisateurId);

        return evenements.stream()
                .map(EvenementResponse::from)
                .toList();
    }

    @Transactional
    public EvenementResponse update(Long id, EvenementUpdateRequest request) {
        validatePeriod(request.dateDebut(), request.dateFin());
        Evenement evenement = evenementRepository.findById(id)
                .orElseThrow(() -> new EvenementNotFoundException(id));

        EvenementStatut currentStatus = EvenementStatut.valueOf(evenement.getStatut());
        EvenementStatut requestedStatus = EvenementStatut.valueOf(request.statut());
        if (currentStatus.isTerminal() || !currentStatus.canTransitionTo(requestedStatus)) {
            throw new InvalidStatusTransitionException(evenement.getStatut(), request.statut());
        }

        evenement.update(
                request.titre(),
                request.description(),
                request.dateDebut(),
                request.dateFin(),
                request.lieu(),
                request.statut());

        return EvenementResponse.from(evenementRepository.saveAndFlush(evenement));
    }

    @Transactional
    public EvenementResponse updateStatus(Long id, EvenementStatusUpdateRequest request) {
        Evenement evenement = evenementRepository.findById(id)
                .orElseThrow(() -> new EvenementNotFoundException(id));
        EvenementStatut currentStatus = EvenementStatut.valueOf(evenement.getStatut());

        if (!currentStatus.canTransitionTo(request.statut()) || currentStatus == request.statut()) {
            throw new InvalidStatusTransitionException(evenement.getStatut(), request.statut().name());
        }

        evenement.updateStatut(request.statut().name());
        return EvenementResponse.from(evenementRepository.saveAndFlush(evenement));
    }

    @Transactional
    public void delete(Long id) {
        Evenement evenement = evenementRepository.findById(id)
                .orElseThrow(() -> new EvenementNotFoundException(id));

        try {
            evenementRepository.delete(evenement);
            evenementRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new EvenementInUseException(id);
        }
    }

    private void validatePeriod(OffsetDateTime dateDebut, OffsetDateTime dateFin) {
        if (!dateFin.isAfter(dateDebut)) {
            throw new InvalidEventPeriodException();
        }
    }
}
