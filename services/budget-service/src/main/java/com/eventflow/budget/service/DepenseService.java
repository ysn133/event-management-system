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
        Budget budget = budgetRepository.lockById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
        if (budget.isClosed()) return depenseRepository.findAllByBudget_IdOrderByIdAsc(budgetId).stream().map(DepenseResponse::from).toList();
        for (RoomReservation reservation : roomReservationPort.listByEvent(budget.getEvenementId())) {
            // Managed reservations are approved by the write API. Pulling their
            // old snapshot would overwrite a price currently being approved.
            if (reservation.budgetVersion()!=null && reservation.budgetVersion()>0) continue;
            if (!budget.getEvenementId().equals(reservation.evenementId())
                    || reservation.prix() == null || reservation.prix().signum() <= 0 || reservation.dateDebut()==null) {
                continue;
            }

            Depense depense = depenseRepository.findByReservationSalleId(reservation.id())
                    .orElse(null);
            boolean cancelled = "ANNULEE".equals(reservation.statut());
            // Historical unlinked sources require an explicit link/create decision.
            if (depense == null) {
                continue;
            }

            String roomName = reservation.nomSalle() == null || reservation.nomSalle().isBlank()
                    ? "Salle " + reservation.salleId()
                    : reservation.nomSalle().trim();
            java.time.LocalDate expenseDate = reservation.dateDebut().toLocalDate();
            // Historical mismatches are reviewed, not silently repriced after payment.
            if (cancelled || depense.getMontant().compareTo(reservation.prix())==0
                    && ("EN_ATTENTE".equals(depense.getStatut()) || SourceExpenseRules.paid(depense)))
                SourceExpenseRules.apply(depense,"Réservation - " + roomName,reservation.prix(),"Salle",expenseDate,reservation.statut());
            depenseRepository.save(depense);
        }
        depenseRepository.flush();
        return depenseRepository.findAllByBudget_IdOrderByIdAsc(budgetId)
                .stream().map(DepenseResponse::from).toList();
    }

    @Transactional
    public DepenseResponse create(Long budgetId, DepenseCreateRequest request) {
        Budget budget = budgetRepository.lockById(budgetId)
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
        ensureManualExpense(depense);
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
        if (depense.getReservationSalleId()!=null || depense.getContratPrestataireId()!=null) {
            if (!"PAYEE".equals(request.statut()) || !("EN_ATTENTE".equals(depense.getStatut()) || "PAYEE".equals(depense.getStatut()))
                    || depense.isSourceCancelled() || depense.isSourceMissing())
                throw new BudgetExceededException("Seul le paiement d’une dépense liée active peut être enregistré ici. Annulez depuis le service d’origine.");
            depense.updateStatut("PAYEE");
            return DepenseResponse.from(depenseRepository.saveAndFlush(depense));
        }
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
        ensureManualExpense(depense);

        depenseRepository.delete(depense);
        depenseRepository.flush();
    }

    private void ensureOpen(Budget budget) {
        Budget locked = budgetRepository.lockById(budget.getId())
                .orElseThrow(() -> new BudgetNotFoundException(budget.getId()));
        if (locked.isClosed()) {
            throw new BudgetClosedException(budget.getId());
        }
    }

    private void ensureManualExpense(Depense expense) {
        if (expense.getContratPrestataireId()!=null || expense.getReservationSalleId()!=null)
            throw new BudgetExceededException("Cette dépense est gérée par un service externe. Modifiez le contrat ou la réservation depuis l’événement.");
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
