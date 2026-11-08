package com.eventflow.evenement.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DatabaseOrganisateurValidationAdapterTest {

    private static final String ORGANIZER_EXISTS_SQL =
            "select exists(select 1 from organisateurs where id = ?)";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DatabaseOrganisateurValidationAdapter adapter;

    @Test
    void returnsTrueWhenOrganizerExists() {
        when(jdbcTemplate.queryForObject(
                eq(ORGANIZER_EXISTS_SQL),
                eq(Boolean.class),
                eq(1L)))
                .thenReturn(true);

        assertThat(adapter.existsById(1L)).isTrue();
    }

    @Test
    void returnsFalseWhenOrganizerDoesNotExist() {
        when(jdbcTemplate.queryForObject(
                eq(ORGANIZER_EXISTS_SQL),
                eq(Boolean.class),
                eq(999L)))
                .thenReturn(false);

        assertThat(adapter.existsById(999L)).isFalse();
    }

    @Test
    void returnsFalseForNullIdWithoutQueryingDatabase() {
        assertThat(adapter.existsById(null)).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }
}
