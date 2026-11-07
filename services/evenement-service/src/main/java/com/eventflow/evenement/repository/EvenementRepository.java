package com.eventflow.evenement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventflow.evenement.domain.Evenement;

public interface EvenementRepository extends JpaRepository<Evenement, Long> {

    List<Evenement> findAllByOrderByIdAsc();

    List<Evenement> findAllByOrganisateurIdOrderByIdAsc(Long organisateurId);
}
