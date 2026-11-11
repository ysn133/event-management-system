package com.eventflow.budget.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budgets_evenement_id",
                columnNames = "evenement_id"))
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evenement_id", nullable = false, unique = true)
    private Long evenementId;

    @Column(name = "montant_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal montantTotal;

    @Column(nullable = false, length = 20)
    private String statut;

    protected Budget() {
    }

    public Budget(Long evenementId, BigDecimal montantTotal) {
        this.evenementId = evenementId;
        this.montantTotal = montantTotal;
        this.statut = BudgetStatut.OUVERT.name();
    }

    public Long getId() {
        return id;
    }

    public Long getEvenementId() {
        return evenementId;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal;
    }

    public void updateMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
    }

    public String getStatut() {
        return statut;
    }

    public boolean isClosed() {
        return BudgetStatut.CLOTURE.name().equals(statut);
    }

    public void updateStatut(BudgetStatut statut) {
        this.statut = statut.name();
    }
}
