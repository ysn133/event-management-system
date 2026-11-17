package com.eventflow.budget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventflow.budget.domain.*;
import com.eventflow.budget.dto.DepenseResponse;
import com.eventflow.budget.exception.*;
import com.eventflow.budget.integration.*;
import com.eventflow.budget.repository.*;

/** An explicit review of historical records, never a description/amount matcher. */
@Service
public class ExpenseReconciliationService {
    public record Source(String type,Long id,String description,BigDecimal amount,LocalDate date,String status,Long version,boolean accepted) {
        String key() { return type+":"+id; }
        String category() { return "ROOM".equals(type)?"Salle":"Prestataire"; }
    }
    public record Issue(String type,Long sourceId,String reason,String description,BigDecimal amount,
            boolean cancelled,Long expenseId,String token) {}
    public record Preview(List<Issue> issues,List<DepenseResponse> manualExpenses) {}
    public record Decision(String type,Long sourceId,String action,Long expenseId,String token,boolean confirmNoExistingExpense) {}
    private final BudgetRepository budgets;
    private final DepenseRepository expenses;
    private final RoomReservationPort rooms;
    private final ProviderContractPort providers;
    public ExpenseReconciliationService(BudgetRepository budgets,DepenseRepository expenses,RoomReservationPort rooms,ProviderContractPort providers) {
        this.budgets=budgets; this.expenses=expenses; this.rooms=rooms; this.providers=providers;
    }
    private Map<String,Source> sources(Long event,String authorization) {
        Map<String,Source> result=new LinkedHashMap<>();
        for (var room:rooms.listByEvent(event)) {
            if (!event.equals(room.evenementId()) || room.id()==null || room.prix()==null || room.dateDebut()==null)
                throw new SourceServiceUnavailableException();
            var source=new Source("ROOM",room.id(),"Réservation - "+(room.nomSalle()==null?"Salle "+room.salleId():room.nomSalle().trim()),
                    room.prix(),room.dateDebut().toLocalDate(),room.statut(),room.budgetVersion()==null?0L:room.budgetVersion(),
                    room.budgetVersion()==null || room.budgetVersion()==0 || Boolean.TRUE.equals(room.budgetApproved()) && "SYNCED".equals(room.budgetSyncState()));
            // Managed sources are already linked by the approval API; unapproved proposals cannot be imported.
            result.put(source.key(),source);
        }
        for(var contract:providers.listByEvent(event,authorization)) {
            if (!event.equals(contract.evenementId()) || contract.id()==null || contract.montant()==null || contract.datePrestation()==null)
                throw new SourceServiceUnavailableException();
            var source=new Source("PROVIDER",contract.id(),contract.prestation(),contract.montant(),contract.datePrestation().toLocalDate(),
                    contract.statut(),contract.budgetSyncVersion()==null?0L:contract.budgetSyncVersion(),"SYNCED".equals(contract.budgetSyncState()));
            result.put(source.key(),source);
        }
        return result;
    }
    private String key(Depense expense) {
        if(expense.getReservationSalleId()!=null) return "ROOM:"+expense.getReservationSalleId();
        if(expense.getContratPrestataireId()!=null) return "PROVIDER:"+expense.getContratPrestataireId();
        return null;
    }
    private String token(Source source,Depense expense) {
        String terms=source==null?"MISSING":source.key()+":"+source.version()+":"+source.accepted()+":"+
                SourceExpenseRules.fingerprint(source.description(),source.amount(),source.date(),source.status());
        return SourceExpenseRules.fingerprint(terms,expense==null?BigDecimal.ZERO:expense.getMontant(),LocalDate.of(2000,1,1),
                expense==null?"NEW":expense.getId()+":"+expense.getUpdatedAt()+":"+expense.getStatut());
    }
    private Preview preview(List<Depense> rows,Map<String,Source> sources) {
        List<Issue> issues=new ArrayList<>();
        Set<String> linked=new HashSet<>();
        for(var expense:rows) {
            String key=key(expense);
            if(key==null) continue;
            linked.add(key);
            var source=sources.get(key);
            if(source==null) {
                if(expense.getSourceReviewedAt()==null) issues.add(new Issue(key.split(":")[0],Long.valueOf(key.split(":")[1]),"SOURCE_MISSING",
                        expense.getDescription(),expense.getMontant(),false,expense.getId(),token(null,expense)));
            } else if(source.accepted() || "ROOM".equals(source.type()) && source.version()==0) {
                boolean differs=!SourceExpenseRules.matches(expense,source.description(),source.amount(),source.date(),source.status());
                if(differs || expense.isSourceMissing()) {
                    String reason=SourceExpenseRules.paid(expense) && !SourceExpenseRules.cancelled(source.status())
                            && expense.getMontant().compareTo(source.amount())!=0?"PAID_AMOUNT_DIFFERENCE":"STALE_EXPENSE";
                    issues.add(new Issue(source.type(),source.id(),reason,source.description(),source.amount(),SourceExpenseRules.cancelled(source.status()),expense.getId(),token(source,expense)));
                }
            }
        }
        for(var source:sources.values()) if(source.accepted() && !linked.contains(source.key()))
            issues.add(new Issue(source.type(),source.id(),"UNLINKED_SOURCE",source.description(),source.amount(),SourceExpenseRules.cancelled(source.status()),null,token(source,null)));
        return new Preview(issues,rows.stream().filter(row->key(row)==null).map(DepenseResponse::from).toList());
    }
    @Transactional(readOnly=true)
    public Preview preview(Long budgetId,String authorization) {
        var budget=budgets.findById(budgetId).orElseThrow(()->new BudgetNotFoundException(budgetId));
        return preview(expenses.findAllByBudget_IdOrderByIdAsc(budgetId),sources(budget.getEvenementId(),authorization));
    }
    @Transactional
    public DepenseResponse reconcile(Long budgetId,Decision decision,String authorization) {
        var budget=budgets.lockById(budgetId).orElseThrow(()->new BudgetNotFoundException(budgetId));
        if(budget.isClosed()) throw new BudgetClosedException(budgetId);
        var rows=expenses.findAllByBudget_IdOrderByIdAsc(budgetId);
        BigDecimal originalTotal=expenses.calculateTotalsByBudgetId(budgetId).getTotalEngage();
        var sources=sources(budget.getEvenementId(),authorization);
        var issue=preview(rows,sources).issues().stream().filter(i->Objects.equals(i.type(),decision.type()) && Objects.equals(i.sourceId(),decision.sourceId()))
                .findFirst().orElseThrow(()->new BudgetExceededException("Ce rapprochement a déjà été traité. Actualisez."));
        if(!Objects.equals(issue.token(),decision.token())) throw new BudgetExceededException("Les données ont changé. Actualisez le rapprochement.");
        var source=sources.get(decision.type()+":"+decision.sourceId());
        Depense expense=issue.expenseId()==null?null:rows.stream().filter(e->e.getId().equals(issue.expenseId())).findFirst().orElseThrow();
        BigDecimal old=expense!=null && engaged(expense)?expense.getMontant():BigDecimal.ZERO;
        if("SOURCE_MISSING".equals(issue.reason())) {
            if(!"RESOLVE_MISSING".equals(decision.action())) throw new IllegalArgumentException("Confirmez explicitement la source absente.");
            if(!SourceExpenseRules.paid(expense)) expense.updateStatut("ANNULEE");
            expense.acknowledgeMissingSource();
        } else if("UNLINKED_SOURCE".equals(issue.reason())) {
            var existing="ROOM".equals(source.type())?expenses.findByReservationSalleId(source.id()):expenses.findByContratPrestataireId(source.id());
            if(existing.isPresent()) throw new BudgetExceededException("Cette source est déjà liée à une dépense d’un autre budget.");
            if("LINK".equals(decision.action())) {
                expense=rows.stream().filter(e->e.getId().equals(decision.expenseId()) && key(e)==null).findFirst()
                        .orElseThrow(()->new IllegalArgumentException("Choisissez une dépense manuelle de ce budget, non liée."));
                if(expense.getMontant().compareTo(source.amount())!=0 || "ANNULEE".equals(expense.getStatut()))
                    throw new IllegalArgumentException("Le montant doit correspondre et la dépense ne doit pas être annulée.");
                old=engaged(expense)?expense.getMontant():BigDecimal.ZERO;
            } else if("CREATE".equals(decision.action()) && decision.confirmNoExistingExpense() && !issue.cancelled()) {
                expense=new Depense(budget,source.description(),source.amount(),source.category(),source.date(),"EN_ATTENTE");
            } else throw new IllegalArgumentException("Liez une dépense existante ou confirmez explicitement son absence avant de créer.");
            if("ROOM".equals(source.type())) expense.linkRoomReservation(source.id(),source.version());
            else expense.linkProviderContract(source.id(),source.version());
            SourceExpenseRules.apply(expense,source.description(),source.amount(),source.category(),source.date(),source.status());
        } else {
            if(!"SYNC".equals(decision.action()) || "PAID_AMOUNT_DIFFERENCE".equals(issue.reason()))
                throw new BudgetExceededException("Le paiement enregistré est conservé. Corrigez le montant dans le service d’origine après vérification.");
            SourceExpenseRules.apply(expense,source.description(),source.amount(),source.category(),source.date(),source.status());
            if("ROOM".equals(source.type())) expense.linkRoomReservation(source.id(),source.version());
            else expense.linkProviderContract(source.id(),source.version());
        }
        BigDecimal added=(engaged(expense)?expense.getMontant():BigDecimal.ZERO).subtract(old);
        if(!budget.isDepassementAutorise() && added.signum()>0 && originalTotal.add(added).compareTo(budget.getMontantTotal())>0)
            throw new BudgetExceededException("Solde insuffisant. Ajustez le budget avant de rapprocher cette dépense.");
        return DepenseResponse.from(expenses.saveAndFlush(expense));
    }
    private boolean engaged(Depense expense) { return "EN_ATTENTE".equals(expense.getStatut()) || SourceExpenseRules.paid(expense); }
}
