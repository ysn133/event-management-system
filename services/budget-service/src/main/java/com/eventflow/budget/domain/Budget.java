package com.eventflow.budget.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(
        name = "uk_budgets_evenement_id", columnNames = "evenement_id"))
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

    @Column(name = "depassement_autorise", nullable = false)
    private boolean depassementAutorise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {}

    public Budget(Long evenementId, BigDecimal montantTotal) {
        this(evenementId, montantTotal, true);
    }

    public Budget(Long evenementId, BigDecimal montantTotal, boolean depassementAutorise) {
        this.evenementId = evenementId;
        this.montantTotal = montantTotal;
        this.depassementAutorise = depassementAutorise;
        this.statut = BudgetStatut.OUVERT.name();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void update(BigDecimal montantTotal, boolean depassementAutorise) {
        this.montantTotal = montantTotal;
        this.depassementAutorise = depassementAutorise;
    }

    public boolean isClosed() { return BudgetStatut.CLOTURE.name().equals(statut); }
    public void updateStatut(BudgetStatut statut) { this.statut = statut.name(); }
    public Long getId() { return id; }
    public Long getEvenementId() { return evenementId; }
    public BigDecimal getMontantTotal() { return montantTotal; }
    public String getStatut() { return statut; }
    public boolean isDepassementAutorise() { return depassementAutorise; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
