package com.bct.rca.controller;

import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.service.RcaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/rca")
@RequiredArgsConstructor
public class RcaController {

    private final RcaService rcaService;

    @GetMapping
    public ResponseEntity<Page<IncidentAnalysis>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rcaService.getAll(PageRequest.of(page, size)));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<IncidentAnalysis>> getRecent(@RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(rcaService.getRecent(hours));
    }

    @GetMapping("/open")
    public ResponseEntity<List<IncidentAnalysis>> getOpenIncidents() {
        return ResponseEntity.ok(rcaService.getOpenIncidents());
    }

    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<IncidentAnalysis>> getByResource(@PathVariable String resourceId) {
        return ResponseEntity.ok(rcaService.getByResource(resourceId));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<IncidentAnalysis> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(rcaService.resolve(id));
    }

    /**
     * Corriger manuellement la catégorie de cause d'un incident.
     * Body : { "category": "MEMORY_EXHAUSTION" }
     *
     * L'auteur n'est PAS pris dans le corps de la requête : il est lu dans le
     * jeton Keycloak que ce service a lui-même vérifié. Un client ne peut donc
     * pas écrire le nom de son choix dans ce qui devient un journal d'audit.
     */
    @PatchMapping("/{id}/category")
    public ResponseEntity<IncidentAnalysis> correctCategory(
            @PathVariable Long id, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String correctedBy = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(rcaService.correctCategory(id, body.get("category"), correctedBy));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(rcaService.getStats());
    }

    @PostMapping("/analyze")
    public ResponseEntity<IncidentAnalysis> analyzeManually(@RequestBody Map<String, Object> event) {
        return ResponseEntity.ok(rcaService.analyzeAnomaly(event));
    }

    /**
     * Un identifiant qui n'existe pas est une erreur du client, pas du serveur :
     * sans ce traitement Spring répondait 500 et écrivait une trace d'erreur
     * complète dans les logs pour une simple faute de frappe.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    /** Catégorie de correction vide, page négative... : requête invalide, donc 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
