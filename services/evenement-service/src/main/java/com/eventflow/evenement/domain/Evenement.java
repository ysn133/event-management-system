package com.eventflow.evenement.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "evenements")
public class Evenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisateur_id", nullable = false)
    private Long organisateurId;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "date_debut", nullable = false)
    private OffsetDateTime dateDebut;

    @Column(name = "date_fin", nullable = false)
    private OffsetDateTime dateFin;

    @Column(length = 255)
    private String lieu;

    @Column(nullable = false, length = 30)
    private String statut;

    protected Evenement() {
    }

    public Evenement(
            Long organisateurId,
            String titre,
            String description,
            OffsetDateTime dateDebut,
            OffsetDateTime dateFin,
            String lieu,
            String statut) {
        this.organisateurId = organisateurId;
        this.titre = titre;
        this.description = description;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.lieu = lieu;
        this.statut = statut;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganisateurId() {
        return organisateurId;
    }

    public String getTitre() {
        return titre;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getDateDebut() {
        return dateDebut;
    }

    public OffsetDateTime getDateFin() {
        return dateFin;
    }

    public String getLieu() {
        return lieu;
    }

    public String getStatut() {
        return statut;
    }

    public void update(
            String titre,
            String description,
            OffsetDateTime dateDebut,
            OffsetDateTime dateFin,
            String lieu,
            String statut) {
        this.titre = titre;
        this.description = description;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.lieu = lieu;
        this.statut = statut;
    }
}
