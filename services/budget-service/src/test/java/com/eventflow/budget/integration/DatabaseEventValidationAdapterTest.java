package com.eventflow.budget.integration;

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
class DatabaseEventValidationAdapterTest {

    private static final String EVENT_EXISTS_SQL =
            "select exists(select 1 from evenements where id = ?)";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DatabaseEventValidationAdapter adapter;

    @Test
    void returnsTrueWhenEventExists() {
        when(jdbcTemplate.queryForObject(
                eq(EVENT_EXISTS_SQL),
                eq(Boolean.class),
                eq(1L)))
                .thenReturn(true);

        assertThat(adapter.existsById(1L)).isTrue();
    }

    @Test
    void returnsFalseWhenEventDoesNotExist() {
        when(jdbcTemplate.queryForObject(
                eq(EVENT_EXISTS_SQL),
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
