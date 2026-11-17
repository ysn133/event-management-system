package com.eventflow.budget.controller;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.eventflow.budget.dto.*;
import com.eventflow.budget.service.RoomCommitmentService;
@Validated @RestController
public class RoomCommitmentController {
    private final RoomCommitmentService service;
    private final String key;
    public RoomCommitmentController(RoomCommitmentService service,@Value("${ROOM_BUDGET_SERVICE_KEY:}") String key) { this.service=service; this.key=key; }
    @PutMapping("/api/budgets/evenement/{eventId}/reservations-salle/{reservationId}")
    public DepenseResponse synchronize(@PathVariable @Positive Long eventId,@PathVariable @Positive Long reservationId,
            @RequestHeader(value="X-Service-Key",defaultValue="") String supplied,@Valid @RequestBody RoomCommitmentRequest request) {
        if (key.isBlank() || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Room service credentials required");
        return service.synchronize(eventId,reservationId,request);
    }
}
