package com.eventflow.budget;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.eventflow.budget.domain.Budget;
import com.eventflow.budget.dto.ProviderContractExpenseRequest;
import com.eventflow.budget.repository.*;
import com.eventflow.budget.service.ProviderContractExpenseService;

@SpringBootTest(properties="CONTRACT_SERVICE_KEY=provider-test-key")
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser
class ProviderContractBudgetIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired BudgetRepository budgets;
    @Autowired DepenseRepository expenses;
    @Autowired ProviderContractExpenseService service;
    Budget budget;
    String url(long contract) { return "/api/budgets/evenement/81001/contrats-prestataire/"+contract; }
    String body(int amount, int version, String status) {
        return "{\"description\":\"Buffet\",\"montant\":"+amount+",\"dateDepense\":\"2027-01-10\",\"statut\":\""+status+"\",\"version\":"+version+"}";
    }
    @BeforeEach void setup() {
        expenses.deleteAll(); budgets.deleteAll();
        budget=budgets.saveAndFlush(new Budget(81001L,new BigDecimal("1000"),false));
    }
    void sync(int amount,int version,String status) throws Exception {
        mvc.perform(put(url(41)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(amount,version,status))).andExpect(status().isOk());
    }
    @Test void recordsOneExpenseAndReplaysWithoutDoubleCharging() throws Exception {
        sync(700,1,"CONFIRME"); sync(700,1,"CONFIRME");
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("700");
    }
    @Test void refusesOverspendingWithoutSavingExpense() throws Exception {
        mvc.perform(put(url(41)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(1001,1,"CONFIRME"))).andExpect(status().isConflict());
        assertThat(expenses.count()).isZero();
    }
    @Test void permitsOverspendingWhenEnabled() throws Exception {
        budget.update(new BigDecimal("1000"),true); budgets.saveAndFlush(budget);
        sync(1200,1,"CONFIRME");
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("1200");
    }
    @Test void updatesAndCancelsTheSameExpense() throws Exception {
        sync(700,1,"CONFIRME"); sync(800,2,"CONFIRME"); sync(800,3,"ANNULE");
        assertThat(expenses.count()).isEqualTo(1);
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalEngage()).isEqualByComparingTo("0");
        mvc.perform(put(url(41)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(700,1,"CONFIRME"))).andExpect(status().isConflict());
    }
    @Test void paidContractIsNotUnpaidBySynchronizationOrCancellation() throws Exception {
        sync(700,1,"CONFIRME");
        var expense=expenses.findByContratPrestataireId(41L).orElseThrow();
        mvc.perform(patch("/api/depenses/"+expense.getId()+"/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"PAYEE\"}"))
                .andExpect(status().isOk());
        sync(700,2,"TERMINE");
        mvc.perform(put(url(41)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(800,3,"CONFIRME"))).andExpect(status().isConflict());
        sync(700,4,"ANNULE"); sync(700,4,"ANNULE");
        var saved=expenses.findByContratPrestataireId(41L).orElseThrow();
        assertThat(saved.getStatut()).isEqualTo("PAYEE");
        assertThat(saved.isSourceCancelled()).isTrue();
        assertThat(expenses.calculateTotalsByBudgetId(budget.getId()).getTotalPaye()).isEqualByComparingTo("700");
    }
    @Test void closedBudgetRejectsNewCommitmentButAcceptsExactReplay() throws Exception {
        sync(700,1,"CONFIRME");
        budget.updateStatut(com.eventflow.budget.domain.BudgetStatut.CLOTURE); budgets.saveAndFlush(budget);
        sync(700,1,"CONFIRME");
        mvc.perform(put(url(42)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(100,1,"CONFIRME"))).andExpect(status().isConflict());
    }
    @Test void requiresBudgetAndServiceCredentials() throws Exception {
        mvc.perform(put(url(41)).contentType(MediaType.APPLICATION_JSON).content(body(100,1,"CONFIRME")))
                .andExpect(status().isForbidden());
        budgets.deleteAll();
        mvc.perform(put(url(41)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(100,1,"CONFIRME"))).andExpect(status().isNotFound());
    }
    @Test void rejectsConflictingReplayAndManualExpenseMutation() throws Exception {
        sync(700,1,"CONFIRME");
        mvc.perform(put(url(41)).header("X-Service-Key","provider-test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(800,1,"CONFIRME"))).andExpect(status().isConflict());
        var expense=expenses.findByContratPrestataireId(41L).orElseThrow();
        mvc.perform(delete("/api/depenses/"+expense.getId())).andExpect(status().isConflict());
        assertThat(expenses.count()).isEqualTo(1);
    }
    @Test void serializesConcurrentCommitmentsAgainstTheSameBalance() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> first=() -> commit(start,61L);
            Callable<Boolean> second=() -> commit(start,62L);
            var a=executor.submit(first); var b=executor.submit(second); start.countDown();
            assertThat(java.util.List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(expenses.count()).isEqualTo(1);
    }
    boolean commit(CountDownLatch start,Long id) throws Exception {
        start.await();
        try { service.synchronize(81001L,id,new ProviderContractExpenseRequest("Buffet",new BigDecimal("700"),LocalDate.of(2027,1,10),"CONFIRME",1L)); return true; }
        catch(com.eventflow.budget.exception.BudgetExceededException expected) { return false; }
    }
}
