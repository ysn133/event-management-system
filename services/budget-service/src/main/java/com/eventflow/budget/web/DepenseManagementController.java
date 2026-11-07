package com.eventflow.budget.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventflow.budget.service.DepenseService;
import com.eventflow.budget.web.dto.DepenseResponse;
import com.eventflow.budget.web.dto.DepenseUpdateRequest;

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
}
