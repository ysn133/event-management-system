package com.eventflow.budget.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventflow.budget.domain.Depense;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    List<Depense> findAllByBudget_IdOrderByIdAsc(Long budgetId);
}
