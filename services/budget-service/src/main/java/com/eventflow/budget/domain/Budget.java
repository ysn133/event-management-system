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

    protected Budget() {
    }

    public Budget(Long evenementId, BigDecimal montantTotal) {
        this.evenementId = evenementId;
        this.montantTotal = montantTotal;
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
}
