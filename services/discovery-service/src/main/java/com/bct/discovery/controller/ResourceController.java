package com.bct.discovery.controller;

import com.bct.discovery.dto.RegisterResourceRequest;
import com.bct.discovery.dto.ResourceDTO;
import com.bct.discovery.model.ResourceStatus;
import com.bct.discovery.model.ResourceType;
import com.bct.discovery.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping("/register")
    public ResponseEntity<ResourceDTO> register(@Valid @RequestBody RegisterResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceService.register(request));
    }

    @GetMapping
    public ResponseEntity<List<ResourceDTO>> findAll() {
        return ResponseEntity.ok(resourceService.findAll());
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<ResourceDTO> findById(@PathVariable String resourceId) {
        return ResponseEntity.ok(resourceService.findById(resourceId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ResourceDTO>> findByStatus(@PathVariable ResourceStatus status) {
        return ResponseEntity.ok(resourceService.findByStatus(status));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ResourceDTO>> findByType(@PathVariable ResourceType type) {
        return ResponseEntity.ok(resourceService.findByType(type));
    }

    @PatchMapping("/{resourceId}/status")
    public ResponseEntity<ResourceDTO> updateStatus(
            @PathVariable String resourceId,
            @RequestParam ResourceStatus status) {
        return ResponseEntity.ok(resourceService.updateStatus(resourceId, status));
    }

    @DeleteMapping("/{resourceId}")
    public ResponseEntity<Void> delete(@PathVariable String resourceId) {
        resourceService.delete(resourceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(resourceService.getStats());
    }
}
