package com.eventflow.evenement.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "integrations.organisateurs.mode",
        havingValue = "database",
        matchIfMissing = true)
public class DatabaseOrganisateurValidationAdapter implements OrganisateurValidationPort {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseOrganisateurValidationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsById(Long organisateurId) {
        if (organisateurId == null) {
            return false;
        }

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from organisateurs where id = ?)",
                Boolean.class,
                organisateurId));
    }
}
