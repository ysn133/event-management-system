package com.eventflow.budget.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventflow.budget.domain.Budget;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    boolean existsByEvenementId(Long evenementId);

    Optional<Budget> findByEvenementId(Long evenementId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Budget b where b.id = :id")
    Optional<Budget> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Budget b where b.evenementId = :eventId")
    Optional<Budget> lockByEvenementId(@org.springframework.data.repository.query.Param("eventId") Long eventId);
}
