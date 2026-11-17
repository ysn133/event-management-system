package com.eventflow.budget.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventflow.budget.domain.Depense;
import com.eventflow.budget.dto.*;
import com.eventflow.budget.exception.*;
import com.eventflow.budget.repository.*;

@Service
public class ProviderContractExpenseService {
    private final BudgetRepository budgets;
    private final DepenseRepository expenses;
    private final DepenseService expenseService;
    public ProviderContractExpenseService(BudgetRepository budgets, DepenseRepository expenses, DepenseService expenseService) {
        this.budgets=budgets; this.expenses=expenses; this.expenseService=expenseService;
    }

    @Transactional
    public DepenseResponse synchronize(Long eventId, Long contractId, ProviderContractExpenseRequest request) {
        var budget=budgets.lockByEvenementId(eventId).orElseThrow(() -> new BudgetNotFoundException(eventId));
        var expense=expenses.findByContratPrestataireId(contractId).orElse(null);
        if (expense!=null && !expense.getBudget().getId().equals(budget.getId()))
            throw new BudgetExceededException("Ce contrat appartient à un autre événement.");
        // A replay must remain safe even if the budget was closed after the first success.
        if (expense!=null && expense.getContratPrestataireVersion()!=null && expense.getContratPrestataireVersion() >= request.version()) {
            if (expense.getContratPrestataireVersion() > request.version())
                throw new BudgetExceededException("Une version plus récente du contrat a déjà été traitée.");
            if (!SourceExpenseRules.matches(expense,request.description(),request.montant(),request.dateDepense(),request.statut()))
                throw new BudgetExceededException("Cette version du contrat a déjà été utilisée avec d’autres données.");
            return DepenseResponse.from(expense);
        }
        if (budget.isClosed()) throw new BudgetClosedException(budget.getId());
        expenseService.synchronizeRoomReservations(budget.getId());
        String status=SourceExpenseRules.status(expense,SourceExpenseRules.cancelled(request.statut()));
        var amount=SourceExpenseRules.amount(expense,request.montant(),SourceExpenseRules.cancelled(request.statut()));
        BigDecimal engaged=expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage();
        var previouslyEngaged=engaged;
        if (expense!=null && ("EN_ATTENTE".equals(expense.getStatut()) || "PAYEE".equals(expense.getStatut())))
            engaged=engaged.subtract(expense.getMontant());
        if (!"ANNULEE".equals(status)) engaged=engaged.add(amount);
        if (!budget.isDepassementAutorise() && engaged.compareTo(budget.getMontantTotal())>0 && engaged.compareTo(previouslyEngaged)>0)
            throw new BudgetExceededException("Solde insuffisant : le dépassement de ce budget n’est pas autorisé.");
        if (expense==null) expense=new Depense(budget, request.description(), request.montant(), "Prestataire", request.dateDepense(), status);
        SourceExpenseRules.apply(expense,request.description(),request.montant(),"Prestataire",request.dateDepense(),request.statut());
        expense.linkProviderContract(contractId, request.version());
        return DepenseResponse.from(expenses.saveAndFlush(expense));
    }
}
