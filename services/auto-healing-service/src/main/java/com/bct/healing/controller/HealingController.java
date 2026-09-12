package com.bct.healing.controller;

import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import com.bct.healing.service.HealingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/healing")
@RequiredArgsConstructor
public class HealingController {

    private final HealingService healingService;

    @GetMapping
    public ResponseEntity<Page<HealingAction>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(healingService.getAll(PageRequest.of(page, size)));
    }

    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<HealingAction>> getByResource(@PathVariable String resourceId) {
        return ResponseEntity.ok(healingService.getByResource(resourceId));
    }

    /**
     * Le nom de l'utilisateur n'est PAS un paramètre de la requête : il est lu
     * dans le jeton Keycloak que ce service a lui-même vérifié. Personne ne
     * peut donc déclencher une action au nom de quelqu'un d'autre.
     */
    @PostMapping("/trigger")
    public ResponseEntity<HealingAction> triggerManual(
            @RequestParam String resourceId,
            @RequestParam String resourceName,
            @RequestParam ActionType actionType,
            @RequestParam String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                healingService.triggerManual(resourceId, resourceName, actionType, username, reason));
    }

    /** Une justification vide ou un utilisateur absent est une erreur du client, pas du serveur. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(healingService.getStats());
    }
}
