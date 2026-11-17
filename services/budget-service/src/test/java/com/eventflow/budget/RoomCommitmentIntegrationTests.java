package com.eventflow.budget;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.eventflow.budget.domain.*;
import com.eventflow.budget.repository.*;

@SpringBootTest(properties="ROOM_BUDGET_SERVICE_KEY=room-test-key") @AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser
class RoomCommitmentIntegrationTests {
    @Autowired MockMvc mvc; @Autowired BudgetRepository budgets; @Autowired DepenseRepository expenses;
    Budget budget;
    String path="/api/budgets/evenement/82001/reservations-salle/71";
    String body(int amount,int version,String status) {
        return "{\"description\":\"Room booking\",\"montant\":"+amount+",\"dateDepense\":\"2027-01-10\",\"statut\":\""+status+"\",\"version\":"+version+"}";
    }
    @BeforeEach void setup() { expenses.deleteAll(); budgets.deleteAll(); budget=budgets.saveAndFlush(new Budget(82001L,new BigDecimal("1000"),false)); }
    void sync(int amount,int version,String status,int expected) throws Exception {
        mvc.perform(put(path).header("X-Service-Key","room-test-key").contentType(MediaType.APPLICATION_JSON).content(body(amount,version,status)))
                .andExpect(status().is(expected));
    }
    @Test void rejectsOverspendingAndMissingOrClosedBudget() throws Exception {
        sync(1200,1,"EN_ATTENTE",409); assertThat(expenses.count()).isZero();
        budget.updateStatut(BudgetStatut.CLOTURE); budgets.saveAndFlush(budget); sync(700,1,"EN_ATTENTE",409);
        budgets.deleteAll(); sync(700,1,"EN_ATTENTE",404);
    }
    @Test void allowsExplicitOverspendingAndReplaysWithoutDuplicates() throws Exception {
        budget.update(new BigDecimal("1000"),true); budgets.saveAndFlush(budget);
        sync(1200,1,"EN_ATTENTE",200); sync(1200,1,"EN_ATTENTE",200);
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("1200");
    }
    @Test void rejectedIncreaseKeepsPriorExpenseAndCancellationReleasesFunds() throws Exception {
        sync(700,1,"EN_ATTENTE",200); sync(1200,2,"EN_ATTENTE",409);
        assertThat(expenses.findByReservationSalleId(71L).orElseThrow().getMontant()).isEqualByComparingTo("700");
        sync(800,3,"CONFIRMEE",200); sync(800,4,"ANNULEE",200);
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("0");
        sync(700,1,"EN_ATTENTE",409);
    }
    @Test void paymentSurvivesConfirmationCancellationAndReplay() throws Exception {
        sync(700,1,"EN_ATTENTE",200);
        var expense=expenses.findByReservationSalleId(71L).orElseThrow();
        mvc.perform(patch("/api/depenses/"+expense.getId()+"/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"PAYEE\"}"))
                .andExpect(status().isOk());
        sync(700,2,"CONFIRMEE",200);
        sync(650,3,"CONFIRMEE",409);
        sync(650,4,"ANNULEE",200); sync(650,4,"ANNULEE",200);
        var saved=expenses.findByReservationSalleId(71L).orElseThrow();
        assertThat(saved.getStatut()).isEqualTo("PAYEE");
        assertThat(saved.getMontant()).isEqualByComparingTo("700");
        assertThat(saved.isSourceCancelled()).isTrue();
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("700");
        mvc.perform(patch("/api/depenses/"+expense.getId()+"/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"ANNULEE\"}"))
                .andExpect(status().isConflict());
    }
    @Test void sharesBalanceWithProviderExpenses() throws Exception {
        var expense=new Depense(budget,"Provider",new BigDecimal("700"),"Prestataire",java.time.LocalDate.now(),"EN_ATTENTE");
        expense.linkProviderContract(82L,1L); expenses.saveAndFlush(expense);
        sync(400,1,"EN_ATTENTE",409); sync(300,1,"EN_ATTENTE",200);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("1000");
    }
    @Test void cannotBypassPrivateServiceKeyOrEditLinkedExpenseManually() throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body(100,1,"EN_ATTENTE"))).andExpect(status().isForbidden());
        sync(100,1,"EN_ATTENTE",200);
        mvc.perform(delete("/api/depenses/"+expenses.findByReservationSalleId(71L).orElseThrow().getId())).andExpect(status().isConflict());
    }
    @Test void exactReplayStillSucceedsAfterClosure() throws Exception {
        sync(700,1,"EN_ATTENTE",200);
        budget.updateStatut(BudgetStatut.CLOTURE); budgets.saveAndFlush(budget);
        sync(700,1,"EN_ATTENTE",200); sync(800,1,"EN_ATTENTE",409);
    }
    @Test void permitsReducingACommitmentWhenExistingSpendingAlreadyExceedsTheLimit() throws Exception {
        sync(700,1,"EN_ATTENTE",200);
        expenses.saveAndFlush(new Depense(budget,"Historical cost",new BigDecimal("600"),"Autres",java.time.LocalDate.now(),"EN_ATTENTE"));
        sync(500,2,"EN_ATTENTE",200);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("1100");
        sync(500,3,"ANNULEE",200);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("600");
    }
}
