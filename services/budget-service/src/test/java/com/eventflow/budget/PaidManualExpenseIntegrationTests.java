package com.eventflow.budget;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.eventflow.budget.domain.*;
import com.eventflow.budget.repository.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser
class PaidManualExpenseIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired BudgetRepository budgets;
    @Autowired DepenseRepository expenses;
    Budget budget;
    Depense paid;

    @BeforeEach void setup() {
        expenses.deleteAll(); budgets.deleteAll();
        budget=budgets.saveAndFlush(new Budget(64001L,new BigDecimal("1000"),false));
        paid=expenses.saveAndFlush(new Depense(budget,"Paid manual cost",new BigDecimal("300"),"Transport",LocalDate.of(2026,11,17),"PAYEE"));
    }
    String path(long id) { return "/api/depenses/"+id; }
    String edit(int amount,String status) {
        return """
            {"description":"Modified label","montant":%d,"categorie":"Other","dateDepense":"2026-11-18","statut":"%s"}
            """.formatted(amount,status);
    }
    void paymentPreserved() {
        var saved=expenses.findById(paid.getId()).orElseThrow();
        assertThat(saved.getStatut()).isEqualTo("PAYEE");
        assertThat(saved.getMontant()).isEqualByComparingTo("300");
        assertThat(saved.getDescription()).isEqualTo("Paid manual cost");
        assertThat(saved.getCategorie()).isEqualTo("Transport");
        assertThat(saved.getDateDepense()).isEqualTo(LocalDate.of(2026,11,17));
        var totals=expenses.calculateTotalsByBudgetId(budget.getId());
        assertThat(totals.getTotalPaye()).isEqualByComparingTo("300");
        assertThat(totals.getTotalEngage()).isEqualByComparingTo("300");
    }
    @Test void paidManualExpenseCannotBeDeleted() throws Exception {
        mvc.perform(delete(path(paid.getId()))).andExpect(status().isConflict());
        paymentPreserved();
    }
    @ParameterizedTest @ValueSource(strings={"PREVUE","EN_ATTENTE","ANNULEE"})
    void paidManualExpenseCannotBeRewoundOrCancelled(String status) throws Exception {
        mvc.perform(patch(path(paid.getId())+"/statut").contentType(MediaType.APPLICATION_JSON)
            .content("{\"statut\":\""+status+"\"}")).andExpect(status().isConflict());
        paymentPreserved();
    }
    @ParameterizedTest @ValueSource(ints={200,300,400})
    void fullUpdateCannotRewritePaidRecordEvenWithSameAmount(int amount) throws Exception {
        mvc.perform(put(path(paid.getId())).contentType(MediaType.APPLICATION_JSON).content(edit(amount,"PAYEE")))
            .andExpect(status().isConflict());
        paymentPreserved();
    }
    @Test void fullUpdateCannotBypassStatusProtection() throws Exception {
        mvc.perform(put(path(paid.getId())).contentType(MediaType.APPLICATION_JSON).content(edit(300,"ANNULEE")))
            .andExpect(status().isConflict());
        paymentPreserved();
    }
    @Test void repeatedPaidStatusIsHarmlessAndDoesNotRewriteTheRecord() throws Exception {
        mvc.perform(patch(path(paid.getId())+"/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"PAYEE\"}"))
            .andExpect(status().isOk());
        paymentPreserved();
    }
    @Test void unpaidExpenseRemainsEditableCancellableAndDeletable() throws Exception {
        var unpaid=expenses.saveAndFlush(new Depense(budget,"Unpaid",new BigDecimal("100"),"Other",LocalDate.now(),"EN_ATTENTE"));
        mvc.perform(put(path(unpaid.getId())).contentType(MediaType.APPLICATION_JSON).content(edit(200,"EN_ATTENTE"))).andExpect(status().isOk());
        mvc.perform(patch(path(unpaid.getId())+"/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"ANNULEE\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete(path(unpaid.getId()))).andExpect(status().isNoContent());
        paymentPreserved();
    }
    @Test void expenseBecomesLockedImmediatelyAfterRecordingPayment() throws Exception {
        var unpaid=expenses.saveAndFlush(new Depense(budget,"Unpaid",new BigDecimal("100"),"Other",LocalDate.now(),"EN_ATTENTE"));
        mvc.perform(patch(path(unpaid.getId())+"/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"PAYEE\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete(path(unpaid.getId()))).andExpect(status().isConflict());
        mvc.perform(put(path(unpaid.getId())).contentType(MediaType.APPLICATION_JSON).content(edit(200,"PAYEE")))
            .andExpect(status().isConflict());
        assertThat(expenses.findById(unpaid.getId()).orElseThrow().getMontant()).isEqualByComparingTo("100");
    }
}
