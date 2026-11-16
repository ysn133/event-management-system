package com.eventflow.auth.domain;
import jakarta.persistence.*;
@Entity
@Table(name="app_users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=255) private String email;
    @Column(nullable=false,length=150) private String nom;
    @Column(name="password_hash",nullable=false,length=255) private String passwordHash;
    protected AppUser() {}
    public AppUser(String email, String nom, String passwordHash) { this.email=email; this.nom=nom; this.passwordHash=passwordHash; }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNom() { return nom; }
    public String getPasswordHash() { return passwordHash; }
}
