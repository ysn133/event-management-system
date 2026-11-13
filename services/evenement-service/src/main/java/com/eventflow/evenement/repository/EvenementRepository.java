package com.eventflow.evenement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.eventflow.evenement.domain.Evenement;

public interface EvenementRepository extends JpaRepository<Evenement, Long>, JpaSpecificationExecutor<Evenement> {

    List<Evenement> findAllByOrderByIdAsc();

    List<Evenement> findAllByOrganisateurIdOrderByIdAsc(Long organisateurId);
}
