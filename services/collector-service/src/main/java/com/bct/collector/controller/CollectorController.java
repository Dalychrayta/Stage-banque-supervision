package com.bct.collector.controller;

import com.bct.collector.model.LogEntry;
import com.bct.collector.model.MetricSnapshot;
import com.bct.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final CollectorService collectorService;

    // --- METRICS ---

    @PostMapping("/metrics")
    public ResponseEntity<MetricSnapshot> ingestMetric(@RequestBody MetricSnapshot metric) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectorService.saveMetric(metric));
    }

    @GetMapping("/metrics/{resourceId}")
    public ResponseEntity<List<MetricSnapshot>> getMetrics(@PathVariable String resourceId) {
        return ResponseEntity.ok(collectorService.getMetricsByResource(resourceId));
    }

    @GetMapping("/metrics/{resourceId}/range")
    public ResponseEntity<List<MetricSnapshot>> getMetricsByRange(
            @PathVariable String resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(collectorService.getMetricsByResourceAndRange(resourceId, from, to));
    }

    @GetMapping("/metrics/{resourceId}/latest")
    public ResponseEntity<List<MetricSnapshot>> getLatestMetrics(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(collectorService.getLatestMetrics(resourceId, limit));
    }

    // --- LOGS ---

    @PostMapping("/logs")
    public ResponseEntity<LogEntry> ingestLog(@RequestBody LogEntry logEntry) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectorService.saveLog(logEntry));
    }

    @GetMapping("/logs/{resourceId}")
    public ResponseEntity<List<LogEntry>> getLogs(@PathVariable String resourceId) {
        return ResponseEntity.ok(collectorService.getLogsByResource(resourceId));
    }

    @GetMapping("/logs/errors/recent")
    public ResponseEntity<List<LogEntry>> getRecentErrors(
            @RequestParam(defaultValue = "60") int minutesBack) {
        return ResponseEntity.ok(collectorService.getRecentErrors(minutesBack));
    }

    // --- GENERAL ---

    @GetMapping("/resources")
    public ResponseEntity<List<String>> getMonitoredResources() {
        return ResponseEntity.ok(collectorService.getMonitoredResources());
    }
}
