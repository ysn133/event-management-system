package com.eventflow.budget.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "integrations.events.mode",
        havingValue = "database",
        matchIfMissing = true)
public class DatabaseEventValidationAdapter implements EventValidationPort {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseEventValidationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsById(Long eventId) {
        if (eventId == null) {
            return false;
        }

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from evenements where id = ?)",
                Boolean.class,
                eventId));
    }
}
