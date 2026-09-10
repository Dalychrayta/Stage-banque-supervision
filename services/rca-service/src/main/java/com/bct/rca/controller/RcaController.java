package com.bct.rca.controller;

import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.service.RcaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /** Corriger manuellement la catégorie de cause d'un incident.
     *  Body : { "category": "MEMORY_EXHAUSTION", "correctedBy": "admin" } */
    @PatchMapping("/{id}/category")
    public ResponseEntity<IncidentAnalysis> correctCategory(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                rcaService.correctCategory(id, body.get("category"), body.get("correctedBy")));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(rcaService.getStats());
    }

    @PostMapping("/analyze")
    public ResponseEntity<IncidentAnalysis> analyzeManually(@RequestBody Map<String, Object> event) {
        return ResponseEntity.ok(rcaService.analyzeAnomaly(event));
    }
}
