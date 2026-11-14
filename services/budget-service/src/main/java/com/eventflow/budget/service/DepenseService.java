package com.eventflow.budget.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.dto.DepenseCreateRequest;
import com.eventflow.budget.dto.DepenseFilterRequest;
import com.eventflow.budget.dto.DepenseResponse;
import com.eventflow.budget.dto.DepenseStatusUpdateRequest;
import com.eventflow.budget.dto.DepenseUpdateRequest;
import com.eventflow.budget.exception.BudgetNotFoundException;
import com.eventflow.budget.exception.BudgetClosedException;
import com.eventflow.budget.exception.DepenseNotFoundException;
import com.eventflow.budget.exception.BudgetExceededException;
import com.eventflow.budget.integration.RoomReservation;
import com.eventflow.budget.integration.RoomReservationPort;
import com.eventflow.budget.repository.DepenseTotals;
import com.eventflow.budget.repository.BudgetRepository;
import com.eventflow.budget.repository.DepenseRepository;

@Service
public class DepenseService {

    private final BudgetRepository budgetRepository;
    private final DepenseRepository depenseRepository;
    private final RoomReservationPort roomReservationPort;

    public DepenseService(
            BudgetRepository budgetRepository,
            DepenseRepository depenseRepository,
            RoomReservationPort roomReservationPort) {
        this.budgetRepository = budgetRepository;
        this.depenseRepository = depenseRepository;
        this.roomReservationPort = roomReservationPort;
    }

    @Transactional
    public List<DepenseResponse> synchronizeRoomReservations(Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
        for (RoomReservation reservation : roomReservationPort.listByEvent(budget.getEvenementId())) {
            if (!budget.getEvenementId().equals(reservation.evenementId())
                    || reservation.prix() == null || reservation.prix().signum() <= 0) {
                continue;
            }

            Depense depense = depenseRepository.findByReservationSalleId(reservation.id())
                    .orElse(null);
            boolean cancelled = "ANNULEE".equals(reservation.statut());
            if (depense == null && cancelled) {
                continue;
            }

            String roomName = reservation.nomSalle() == null || reservation.nomSalle().isBlank()
                    ? "Salle " + reservation.salleId()
                    : reservation.nomSalle().trim();
            java.time.LocalDate expenseDate = reservation.dateDebut() == null
                    ? java.time.LocalDate.now()
                    : reservation.dateDebut().toLocalDate();
            String status = cancelled ? "ANNULEE" : "EN_ATTENTE";
            if (depense == null) {
                depense = new Depense(budget, "Réservation - " + roomName,
                        reservation.prix(), "Salle", expenseDate, status);
                depense.linkRoomReservation(reservation.id());
            } else {
                depense.update("Réservation - " + roomName, reservation.prix(),
                        "Salle", expenseDate, status, depense.getCommentaire());
            }
            depenseRepository.save(depense);
        }
        depenseRepository.flush();
        return depenseRepository.findAllByBudget_IdOrderByIdAsc(budgetId)
                .stream().map(DepenseResponse::from).toList();
    }

    @Transactional
    public DepenseResponse create(Long budgetId, DepenseCreateRequest request) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
        ensureOpen(budget);
        ensureWithinBudget(budget, request.montant(), request.statut(), java.math.BigDecimal.ZERO, null);

        Depense depense = new Depense(
                budget,
                request.description(),
                request.montant(),
                request.categorie(),
                request.dateDepense(),
                request.statut());
        depense.updateCommentaire(request.commentaire());

        depense = depenseRepository.saveAndFlush(depense);
        return DepenseResponse.from(depense);
    }

    @Transactional
    public List<DepenseResponse> listByBudget(Long budgetId) {
        if (!budgetRepository.existsById(budgetId)) {
            throw new BudgetNotFoundException(budgetId);
        }
        synchronizeRoomReservations(budgetId);
        return depenseRepository.findAllByBudget_IdOrderByIdAsc(budgetId)
                .stream()
                .map(DepenseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepenseResponse getById(Long id) {
        return depenseRepository.findById(id).map(DepenseResponse::from)
                .orElseThrow(() -> new DepenseNotFoundException(id));
    }

    @Transactional
    public List<DepenseResponse> listByBudget(
            Long budgetId,
            DepenseFilterRequest filters) {
        if (!filters.hasFilters()) {
            return listByBudget(budgetId);
        }
        if (!budgetRepository.existsById(budgetId)) {
            throw new BudgetNotFoundException(budgetId);
        }
        synchronizeRoomReservations(budgetId);

        Specification<Depense> specification = (root, query, builder) ->
                builder.equal(root.get("budget").get("id"), budgetId);
        if (filters.categorie() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    builder.lower(root.get("categorie")),
                    filters.categorie().toLowerCase()));
        }
        if (filters.statut() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    root.get("statut"),
                    filters.statut()));
        }
        if (filters.dateDebut() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("dateDepense"), filters.dateDebut()));
        }
        if (filters.dateFin() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("dateDepense"), filters.dateFin()));
        }
        if (filters.montantMin() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("montant"), filters.montantMin()));
        }
        if (filters.montantMax() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("montant"), filters.montantMax()));
        }

        return depenseRepository.findAll(specification, Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(DepenseResponse::from)
                .toList();
    }

    @Transactional
    public DepenseResponse update(Long depenseId, DepenseUpdateRequest request) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new DepenseNotFoundException(depenseId));
        ensureOpen(depense.getBudget());
        ensureWithinBudget(depense.getBudget(), request.montant(), request.statut(), depense.getMontant(),
                depense.getStatut());

        depense.update(
                request.description(),
                request.montant(),
                request.categorie(),
                request.dateDepense(),
                request.statut(),
                request.commentaire() == null ? depense.getCommentaire() : request.commentaire());

        depense = depenseRepository.saveAndFlush(depense);
        return DepenseResponse.from(depense);
    }

    @Transactional
    public DepenseResponse updateStatus(Long depenseId, DepenseStatusUpdateRequest request) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new DepenseNotFoundException(depenseId));
        ensureOpen(depense.getBudget());
        ensureWithinBudget(depense.getBudget(), depense.getMontant(), request.statut(), depense.getMontant(),
                depense.getStatut());

        depense.updateStatut(request.statut());

        depense = depenseRepository.saveAndFlush(depense);
        return DepenseResponse.from(depense);
    }

    @Transactional
    public void delete(Long depenseId) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new DepenseNotFoundException(depenseId));
        ensureOpen(depense.getBudget());

        depenseRepository.delete(depense);
        depenseRepository.flush();
    }

    private void ensureOpen(Budget budget) {
        if (budget.isClosed()) {
            throw new BudgetClosedException(budget.getId());
        }
    }

    private void ensureWithinBudget(Budget budget, java.math.BigDecimal newAmount, String newStatus,
            java.math.BigDecimal oldAmount, String oldStatus) {
        DepenseTotals totals = depenseRepository.calculateTotalsByBudgetId(budget.getId());
        java.math.BigDecimal engaged = totals == null ? java.math.BigDecimal.ZERO : totals.getTotalEngage();
        if (isEngaged(oldStatus)) engaged = engaged.subtract(oldAmount);
        if (isEngaged(newStatus == null ? "PREVUE" : newStatus)) engaged = engaged.add(newAmount);
        boolean exceeded = engaged.compareTo(budget.getMontantTotal()) > 0;
        if (exceeded && !budget.isDepassementAutorise()) {
            throw new BudgetExceededException("This expense would exceed the allocated budget");
        }
    }

    private boolean isEngaged(String status) {
        return com.eventflow.budget.domain.DepenseStatut.EN_ATTENTE.name().equals(status)
                || com.eventflow.budget.domain.DepenseStatut.PAYEE.name().equals(status);
    }
}
