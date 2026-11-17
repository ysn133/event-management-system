package com.eventflow.budget;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.eventflow.budget.domain.*;
import com.eventflow.budget.exception.*;
import com.eventflow.budget.integration.*;
import com.eventflow.budget.repository.*;
import com.eventflow.budget.service.ExpenseReconciliationService;
import com.eventflow.budget.service.ExpenseReconciliationService.Decision;

@SpringBootTest @ActiveProfiles("test")
class ExpenseReconciliationIntegrationTests {
    @Autowired ExpenseReconciliationService service;
    @Autowired BudgetRepository budgets;
    @Autowired DepenseRepository expenses;
    @MockitoBean RoomReservationPort rooms;
    @MockitoBean ProviderContractPort providers;
    Budget budget;
    @BeforeEach void setup() {
        expenses.deleteAll(); budgets.deleteAll();
        budget=budgets.saveAndFlush(new Budget(83001L,new BigDecimal("1000"),false));
        when(rooms.listByEvent(83001L)).thenReturn(List.of(room("CONFIRMEE",700)));
        when(providers.listByEvent(83001L,"Bearer test")).thenReturn(List.of());
    }
    RoomReservation room(String status,int amount) {
        return new RoomReservation(71L,7L,83001L,"Atlas",OffsetDateTime.parse("2027-01-10T09:00:00Z"),
                OffsetDateTime.parse("2027-01-10T18:00:00Z"),BigDecimal.valueOf(amount),status);
    }
    Depense manual(String status,int amount) {
        return expenses.saveAndFlush(new Depense(budget,"Already recorded",BigDecimal.valueOf(amount),"Salle",LocalDate.of(2027,1,5),status));
    }
    ExpenseReconciliationService.Issue issue() { return service.preview(budget.getId(),"Bearer test").issues().getFirst(); }
    com.eventflow.budget.dto.DepenseResponse decide(String action,Long expenseId,boolean confirmed) {
        var issue=issue();
        return service.reconcile(budget.getId(),new Decision(issue.type(),issue.sourceId(),action,expenseId,issue.token(),confirmed),"Bearer test");
    }
    @Test void previewDoesNotGuessOrCreateEvenWhenManualAmountMatches() {
        manual("PAYEE",700);
        assertThat(issue().reason()).isEqualTo("UNLINKED_SOURCE");
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(service.preview(budget.getId(),"Bearer test").manualExpenses()).hasSize(1);
    }
    @Test void explicitlyLinksPaidManualCostWithoutDuplicateAndPreservesPaymentDate() {
        var manual=manual("PAYEE",700);
        var linked=decide("LINK",manual.getId(),false);
        assertThat(linked.statut()).isEqualTo("PAYEE");
        assertThat(linked.description()).isEqualTo("Already recorded");
        assertThat(linked.dateDepense()).isEqualTo(LocalDate.of(2027,1,5));
        assertThat(linked.reservationSalleId()).isEqualTo(71L);
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(service.preview(budget.getId(),"Bearer test").issues()).isEmpty();
    }
    @Test void creatingRequiresConfirmationAndRespectsRemainingBudget() {
        assertThatThrownBy(()->decide("CREATE",null,false)).isInstanceOf(IllegalArgumentException.class);
        manual("PAYEE",400);
        assertThatThrownBy(()->decide("CREATE",null,true)).isInstanceOf(BudgetExceededException.class);
        assertThat(expenses.count()).isEqualTo(1);
        budget.update(new BigDecimal("1200"),false); budgets.saveAndFlush(budget);
        decide("CREATE",null,true);
        assertThat(expenses.count()).isEqualTo(2);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("1100");
    }
    @Test void rejectsStaleSourceTokenAndCannotLinkWrongBudgetOrAmount() {
        var old=issue();
        when(rooms.listByEvent(83001L)).thenReturn(List.of(room("CONFIRMEE",800)));
        assertThatThrownBy(()->service.reconcile(budget.getId(),new Decision("ROOM",71L,"CREATE",null,old.token(),true),"Bearer test"))
                .isInstanceOf(BudgetExceededException.class);
        var manual=manual("PAYEE",700);
        assertThatThrownBy(()->decide("LINK",manual.getId(),false)).isInstanceOf(IllegalArgumentException.class);
        var other=budgets.saveAndFlush(new Budget(83002L,new BigDecimal("1000"),false));
        var foreign=expenses.saveAndFlush(new Depense(other,"Foreign",new BigDecimal("800"),"Salle",LocalDate.now(),"EN_ATTENTE"));
        assertThatThrownBy(()->decide("LINK",foreign.getId(),false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unavailableSourceNeverMeansMissingAndDoesNotChangeExpenses() {
        var manual=manual("PAYEE",700); decide("LINK",manual.getId(),false);
        when(providers.listByEvent(83001L,"Bearer test")).thenThrow(new SourceServiceUnavailableException());
        assertThatThrownBy(this::issue).isInstanceOf(SourceServiceUnavailableException.class);
        assertThat(expenses.findById(manual.getId()).orElseThrow().getStatut()).isEqualTo("PAYEE");
    }
    @Test void missingPaidSourceRequiresReviewAndNeverReleasesMoney() {
        var manual=manual("PAYEE",700); decide("LINK",manual.getId(),false);
        when(rooms.listByEvent(83001L)).thenReturn(List.of());
        assertThat(issue().reason()).isEqualTo("SOURCE_MISSING");
        var kept=decide("RESOLVE_MISSING",null,false);
        assertThat(kept.statut()).isEqualTo("PAYEE"); assertThat(kept.sourceMissing()).isTrue();
        assertThat(service.preview(budget.getId(),"Bearer test").issues()).isEmpty();
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalPaye()).isEqualByComparingTo("700");
    }
    @Test void confirmedMissingUnpaidSourceReleasesCommitment() {
        decide("CREATE",null,true);
        when(rooms.listByEvent(83001L)).thenReturn(List.of());
        assertThat(decide("RESOLVE_MISSING",null,false).statut()).isEqualTo("ANNULEE");
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("0");
    }
    @Test void rejectsAutomaticRepricingOfHistoricalPaidExpense() {
        var manual=manual("PAYEE",700); decide("LINK",manual.getId(),false);
        when(rooms.listByEvent(83001L)).thenReturn(List.of(room("CONFIRMEE",800)));
        assertThat(issue().reason()).isEqualTo("PAID_AMOUNT_DIFFERENCE");
        assertThatThrownBy(()->decide("SYNC",null,false)).isInstanceOf(BudgetExceededException.class);
        assertThat(expenses.findById(manual.getId()).orElseThrow().getMontant()).isEqualByComparingTo("700");
    }
    @Test void cancelledSourceCanBeLinkedToPaidExpenseButCannotInventUnpaidCost() {
        when(rooms.listByEvent(83001L)).thenReturn(List.of(room("ANNULEE",700)));
        assertThatThrownBy(()->decide("CREATE",null,true)).isInstanceOf(IllegalArgumentException.class);
        var manual=manual("PAYEE",700);
        var linked=decide("LINK",manual.getId(),false);
        assertThat(linked.statut()).isEqualTo("PAYEE"); assertThat(linked.sourceCancelled()).isTrue();
    }
    @Test void legacyProviderContractLinksToExistingPaymentWithoutCreatingAnotherExpense() {
        when(rooms.listByEvent(83001L)).thenReturn(List.of());
        when(providers.listByEvent(83001L,"Bearer test")).thenReturn(List.of(new ProviderContractPort.Contract(81L,83001L,"Buffet",new BigDecimal("700"),
                OffsetDateTime.parse("2027-01-10T12:00:00Z"),"CONFIRME","SYNCED",0L)));
        var manual=manual("PAYEE",700);
        var linked=decide("LINK",manual.getId(),false);
        assertThat(linked.contratPrestataireId()).isEqualTo(81L);
        assertThat(linked.statut()).isEqualTo("PAYEE");
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(service.preview(budget.getId(),"Bearer test").issues()).isEmpty();
    }
    @Test void closedBudgetAndPendingProviderProposalsAreNotImported() {
        when(rooms.listByEvent(83001L)).thenReturn(List.of());
        when(providers.listByEvent(83001L,"Bearer test")).thenReturn(List.of(new ProviderContractPort.Contract(81L,83001L,"Buffet",new BigDecimal("700"),
                OffsetDateTime.parse("2027-01-10T12:00:00Z"),"CONFIRME","PENDING",1L)));
        assertThat(service.preview(budget.getId(),"Bearer test").issues()).isEmpty();
        when(rooms.listByEvent(83001L)).thenReturn(List.of(room("CONFIRMEE",700)));
        budget.updateStatut(BudgetStatut.CLOTURE); budgets.saveAndFlush(budget);
        assertThatThrownBy(()->decide("CREATE",null,true)).isInstanceOf(BudgetClosedException.class);
    }
}
