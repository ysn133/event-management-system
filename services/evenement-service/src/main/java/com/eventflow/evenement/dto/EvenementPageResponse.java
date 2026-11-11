package com.eventflow.evenement.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.eventflow.evenement.domain.Evenement;

public record EvenementPageResponse(
        List<EvenementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static EvenementPageResponse from(Page<Evenement> result) {
        return new EvenementPageResponse(
                result.getContent().stream().map(EvenementResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
