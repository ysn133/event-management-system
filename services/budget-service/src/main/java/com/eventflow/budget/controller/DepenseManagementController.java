package com.eventflow.budget.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventflow.budget.service.DepenseService;
import com.eventflow.budget.dto.DepenseResponse;
import com.eventflow.budget.dto.DepenseStatusUpdateRequest;
import com.eventflow.budget.dto.DepenseUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/depenses")
public class DepenseManagementController {

    private final DepenseService depenseService;

    public DepenseManagementController(DepenseService depenseService) {
        this.depenseService = depenseService;
    }

    @PutMapping("/{id}")
    public DepenseResponse update(
            @PathVariable Long id,
            @Valid @RequestBody DepenseUpdateRequest request) {
        return depenseService.update(id, request);
    }

    @PatchMapping("/{id}/statut")
    public DepenseResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody DepenseStatusUpdateRequest request) {
        return depenseService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        depenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
