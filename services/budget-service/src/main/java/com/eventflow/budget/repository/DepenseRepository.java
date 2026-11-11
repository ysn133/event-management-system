package com.eventflow.budget.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eventflow.budget.domain.Depense;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    List<Depense> findAllByBudget_IdOrderByIdAsc(Long budgetId);

    List<Depense> findAllByBudget_IdAndStatutNotOrderByIdAsc(Long budgetId, String statut);

    @Query("""
            select
                coalesce(sum(case when d.statut = 'PREVUE' then d.montant else 0 end), 0) as totalPrevu,
                coalesce(sum(case when d.statut in ('EN_ATTENTE', 'PAYEE') then d.montant else 0 end), 0) as totalEngage,
                coalesce(sum(case when d.statut = 'PAYEE' then d.montant else 0 end), 0) as totalPaye
            from Depense d
            where d.budget.id = :budgetId
            """)
    DepenseTotals calculateTotalsByBudgetId(@Param("budgetId") Long budgetId);
}
