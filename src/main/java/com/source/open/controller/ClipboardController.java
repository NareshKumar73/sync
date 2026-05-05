package com.source.open.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.source.open.payload.Clipboard;
import com.source.open.util.ClipboardRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clipboard")
@CrossOrigin("*")
public class ClipboardController {

    @Autowired
    private ClipboardRepository clipboardRepository;

    @GetMapping
    public ResponseEntity<List<Clipboard>> getAllClipboards() {
        return ResponseEntity.ok(clipboardRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Clipboard> addClipboard(@RequestBody Clipboard clipboard, HttpServletRequest request) {
        clipboard.setSenderIp(request.getRemoteAddr());
        clipboard.setTimestamp(LocalDateTime.now());
        return ResponseEntity.ok(clipboardRepository.save(clipboard));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClipboard(@PathVariable Long id) {
        clipboardRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllClipboards() {
        clipboardRepository.deleteAll();
        return ResponseEntity.ok().build();
    }
}
