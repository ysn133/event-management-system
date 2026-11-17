package com.eventflow.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.dto.*;
import com.eventflow.budget.exception.*;
import com.eventflow.budget.repository.*;

@Service
public class RoomCommitmentService {
    private final BudgetRepository budgets;
    private final DepenseRepository expenses;
    private final DepenseService expenseService;
    public RoomCommitmentService(BudgetRepository budgets,DepenseRepository expenses,DepenseService expenseService) {
        this.budgets=budgets; this.expenses=expenses; this.expenseService=expenseService;
    }
    @Transactional
    public DepenseResponse synchronize(Long eventId,Long reservationId,RoomCommitmentRequest request) {
        var budget=budgets.lockByEvenementId(eventId).orElseThrow(() -> new BudgetNotFoundException(eventId));
        var expense=expenses.findByReservationSalleId(reservationId).orElse(null);
        if (expense!=null && !expense.getBudget().getId().equals(budget.getId()))
            throw new BudgetExceededException("Cette réservation appartient à un autre événement.");
        if (expense!=null && expense.getReservationSalleVersion()!=null && expense.getReservationSalleVersion()>=request.version()) {
            if (!expense.getReservationSalleVersion().equals(request.version())
                    || !SourceExpenseRules.matches(expense,request.description(),request.montant(),request.dateDepense(),request.statut()))
                throw new BudgetExceededException("Version de réservation périmée ou déjà utilisée avec d’autres données.");
            return DepenseResponse.from(expense);
        }
        if (budget.isClosed()) throw new BudgetClosedException(budget.getId());
        expenseService.synchronizeRoomReservations(budget.getId());
        // A legacy expense may have been discovered by the refresh above.
        expense=expenses.findByReservationSalleId(reservationId).orElse(null);
        var engaged=expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage();
        var previouslyEngaged=engaged;
        if (expense!=null && ("EN_ATTENTE".equals(expense.getStatut()) || "PAYEE".equals(expense.getStatut())))
            engaged=engaged.subtract(expense.getMontant());
        String status=SourceExpenseRules.status(expense,SourceExpenseRules.cancelled(request.statut()));
        var amount=SourceExpenseRules.amount(expense,request.montant(),SourceExpenseRules.cancelled(request.statut()));
        if (!"ANNULEE".equals(status)) engaged=engaged.add(amount);
        if (!budget.isDepassementAutorise() && engaged.compareTo(budget.getMontantTotal())>0 && engaged.compareTo(previouslyEngaged)>0)
            throw new BudgetExceededException("Solde insuffisant : cette réservation dépasserait le budget autorisé.");
        if (expense==null) expense=new Depense(budget,request.description(),request.montant(),"Salle",request.dateDepense(),status);
        SourceExpenseRules.apply(expense,request.description(),request.montant(),"Salle",request.dateDepense(),request.statut());
        expense.linkRoomReservation(reservationId,request.version());
        return DepenseResponse.from(expenses.saveAndFlush(expense));
    }
}
