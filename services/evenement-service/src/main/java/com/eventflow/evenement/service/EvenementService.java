package com.eventflow.evenement.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.evenement.domain.Evenement;
import com.eventflow.evenement.domain.EvenementStatut;
import com.eventflow.evenement.dto.EvenementCreateRequest;
import com.eventflow.evenement.dto.EvenementResponse;
import com.eventflow.evenement.dto.EvenementPageResponse;
import com.eventflow.evenement.dto.EvenementUpdateRequest;
import com.eventflow.evenement.dto.EvenementStatusUpdateRequest;
import com.eventflow.evenement.exception.EvenementInUseException;
import com.eventflow.evenement.exception.EvenementNotFoundException;
import com.eventflow.evenement.exception.InvalidEventPeriodException;
import com.eventflow.evenement.exception.InvalidStatusTransitionException;
import com.eventflow.evenement.exception.InvalidEventSearchException;
import com.eventflow.evenement.exception.OrganisateurNotFoundException;
import com.eventflow.evenement.integration.OrganisateurValidationPort;
import com.eventflow.evenement.repository.EvenementRepository;

@Service
public class EvenementService {

    private static final String DEFAULT_STATUS = "PLANIFIE";
    private static final java.util.Set<String> SORTABLE_FIELDS = java.util.Set.of(
            "id", "titre", "dateDebut", "dateFin", "statut", "organisateurId");

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

    @Transactional(readOnly = true)
    public EvenementPageResponse search(
            String q,
            Long organisateurId,
            EvenementStatut statut,
            OffsetDateTime dateDebut,
            OffsetDateTime dateFin,
            int page,
            int size,
            String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidEventSearchException("Page must be positive and size must be between 1 and 100");
        }
        if (dateDebut != null && dateFin != null && dateFin.isBefore(dateDebut)) {
            throw new InvalidEventSearchException("End date filter must not be before start date filter");
        }

        Sort parsedSort = parseSort(sort);
        Specification<Evenement> specification = Specification.where(null);
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("titre")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("lieu")), pattern)));
        }
        if (organisateurId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("organisateurId"), organisateurId));
        }
        if (statut != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("statut"), statut.name()));
        }
        if (dateDebut != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("dateDebut"), dateDebut));
        }
        if (dateFin != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("dateFin"), dateFin));
        }

        Page<Evenement> result = evenementRepository.findAll(
                specification, PageRequest.of(page, size, parsedSort));
        return EvenementPageResponse.from(result);
    }

    private Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "id,asc" : sort;
        String[] parts = value.split(",", -1);
        if (parts.length > 2 || !SORTABLE_FIELDS.contains(parts[0])) {
            throw new InvalidEventSearchException("Unsupported event sort field");
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventSearchException("Sort direction must be asc or desc");
        }
        return Sort.by(direction, parts[0]);
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
