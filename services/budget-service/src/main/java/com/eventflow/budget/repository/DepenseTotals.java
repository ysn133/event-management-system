package com.eventflow.budget.repository;

import java.math.BigDecimal;

public interface DepenseTotals {

    BigDecimal getTotalPrevu();

    BigDecimal getTotalEngage();

    BigDecimal getTotalPaye();
}
