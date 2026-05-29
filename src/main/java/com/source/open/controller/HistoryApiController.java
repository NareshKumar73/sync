package com.source.open.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.source.open.util.TransferHistoryRepository;
import com.source.open.util.NetworkTrafficService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
@RequiredArgsConstructor
public class HistoryApiController {

    private final TransferHistoryRepository transferHistoryRepository;
    private final NetworkTrafficService networkTrafficService;

    @DeleteMapping("/history")
    public ResponseEntity<Void> deleteAllHistory() {
        transferHistoryRepository.deleteAll();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistoryById(@PathVariable Long id) {
        transferHistoryRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/traffic")
    public ResponseEntity<Void> deleteAllTraffic() {
        networkTrafficService.clearAllTraffic();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/traffic/{id}")
    public ResponseEntity<Void> deleteTrafficById(@PathVariable Long id) {
        networkTrafficService.clearTrafficById(id);
        return ResponseEntity.ok().build();
    }
}
