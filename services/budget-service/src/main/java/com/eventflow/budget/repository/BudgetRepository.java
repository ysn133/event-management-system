package com.eventflow.budget.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventflow.budget.domain.Budget;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    boolean existsByEvenementId(Long evenementId);

    Optional<Budget> findByEvenementId(Long evenementId);
}
