package com.eventflow.evenement.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import com.eventflow.evenement.domain.EvenementStatut;
import com.eventflow.evenement.dto.EvenementPageResponse;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.eventflow.evenement.service.EvenementService;
import com.eventflow.evenement.dto.EvenementCreateRequest;
import com.eventflow.evenement.dto.EvenementResponse;
import com.eventflow.evenement.dto.EvenementUpdateRequest;
import com.eventflow.evenement.dto.EvenementStatusUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/evenements")
public class EvenementController {

    private final EvenementService evenementService;

    public EvenementController(EvenementService evenementService) {
        this.evenementService = evenementService;
    }

    @PostMapping
    public ResponseEntity<EvenementResponse> create(
            @Valid @RequestBody EvenementCreateRequest request) {
        EvenementResponse response = evenementService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public EvenementResponse getById(@PathVariable Long id) {
        return evenementService.getById(id);
    }

    @GetMapping
    public EvenementPageResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long organisateurId,
            @RequestParam(required = false) EvenementStatut statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort) {
        return evenementService.search(q, organisateurId, statut, dateDebut, dateFin, page, size, sort);
    }

    @PutMapping("/{id}")
    public EvenementResponse update(
            @PathVariable Long id,
            @Valid @RequestBody EvenementUpdateRequest request) {
        return evenementService.update(id, request);
    }

    @PatchMapping("/{id}/statut")
    public EvenementResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody EvenementStatusUpdateRequest request) {
        return evenementService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        evenementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
