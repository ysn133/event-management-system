package com.eventflow.budget.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "depenses")
public class Depense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 100)
    private String categorie;

    @Column(name = "date_depense", nullable = false)
    private LocalDate dateDepense;

    @Column(nullable = false, length = 50)
    private String statut;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "reservation_salle_id")
    private Long reservationSalleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Depense() {}

    public Depense(Budget budget, String description, BigDecimal montant,
            String categorie, LocalDate dateDepense, String statut) {
        this.budget = budget;
        this.description = description;
        this.montant = montant;
        this.categorie = categorie;
        this.dateDepense = dateDepense;
        this.statut = statut == null ? DepenseStatut.PREVUE.name() : statut;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void update(String description, BigDecimal montant, String categorie,
            LocalDate dateDepense, String statut, String commentaire) {
        this.description = description;
        this.montant = montant;
        this.categorie = categorie;
        this.dateDepense = dateDepense;
        this.statut = statut;
        this.commentaire = commentaire;
    }

    public void updateStatut(String statut) { this.statut = statut; }
    public void updateCommentaire(String commentaire) { this.commentaire = commentaire; }
    public void linkRoomReservation(Long reservationSalleId) { this.reservationSalleId = reservationSalleId; }
    public Long getId() { return id; }
    public Budget getBudget() { return budget; }
    public String getDescription() { return description; }
    public BigDecimal getMontant() { return montant; }
    public String getCategorie() { return categorie; }
    public LocalDate getDateDepense() { return dateDepense; }
    public String getStatut() { return statut; }
    public String getCommentaire() { return commentaire; }
    public Long getReservationSalleId() { return reservationSalleId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
