package com.eventflow.budget.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eventflow.budget.domain.Depense;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    List<Depense> findAllByBudget_IdOrderByIdAsc(Long budgetId);

    @Query("select coalesce(sum(d.montant), 0) from Depense d where d.budget.id = :budgetId")
    BigDecimal sumMontantByBudgetId(@Param("budgetId") Long budgetId);
}
