package com.source.open.controller;

import com.source.open.util.StorageService;
import com.source.open.util.SystemMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SystemMetricsController {

    private final SystemMetricsService systemMetricsService;
    private final StorageService storageService;

    @GetMapping("/system/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        return ResponseEntity.ok(systemMetricsService.getMetrics());
    }

    @GetMapping("/storage/status")
    public ResponseEntity<Map<String, Object>> getStorageStatus() {
        return ResponseEntity.ok(storageService.getStorageStatus());
    }

    @PostMapping("/storage/reserve")
    public ResponseEntity<?> reserveStorage(@RequestParam String uuid, @RequestParam long size) {
        boolean success = storageService.reserveSpace(uuid, size);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Space reserved successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage limit exceeded"));
        }
    }

    @PostMapping("/storage/release")
    public ResponseEntity<?> releaseStorage(@RequestParam String uuid) {
        storageService.releaseSpace(uuid);
        return ResponseEntity.ok(Map.of("message", "Space released"));
    }
}
