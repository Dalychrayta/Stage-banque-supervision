package com.bct.healing.controller;

import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import com.bct.healing.service.HealingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/trigger")
    public ResponseEntity<HealingAction> triggerManual(
            @RequestParam String resourceId,
            @RequestParam String resourceName,
            @RequestParam ActionType actionType) {
        return ResponseEntity.ok(healingService.triggerManual(resourceId, resourceName, actionType));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(healingService.getStats());
    }
}
