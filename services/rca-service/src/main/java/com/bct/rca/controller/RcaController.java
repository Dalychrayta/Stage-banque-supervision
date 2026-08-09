package com.bct.rca.controller;

import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.service.RcaService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<IncidentAnalysis>> getAll() {
        return ResponseEntity.ok(rcaService.getAll());
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

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(rcaService.getStats());
    }

    @PostMapping("/analyze")
    public ResponseEntity<IncidentAnalysis> analyzeManually(@RequestBody Map<String, Object> event) {
        return ResponseEntity.ok(rcaService.analyzeAnomaly(event));
    }
}
