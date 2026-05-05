package com.source.open.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.source.open.payload.Chat;
import com.source.open.util.ChatRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin("*")
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

    @GetMapping
    public ResponseEntity<List<Chat>> getAllChats() {
        return ResponseEntity.ok(chatRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Chat> addChat(@RequestBody Chat chat, HttpServletRequest request) {
        chat.setSenderIp(request.getRemoteAddr());
        if (chat.getTimestamp() == null) {
            chat.setTimestamp(LocalDateTime.now());
        }
        Chat saved = chatRepository.save(chat);
        return ResponseEntity.ok(saved);
    }

    @org.springframework.messaging.handler.annotation.MessageMapping("/chat.sendMessage")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/public")
    public Chat sendMessage(@org.springframework.messaging.handler.annotation.Payload Chat chat, org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        // If IP is not available from websocket directly, you might store it in session attributes, but for simplicity we rely on what client sends or we can keep it null.
        if (chat.getTimestamp() == null) {
            chat.setTimestamp(LocalDateTime.now());
        }
        return chatRepository.save(chat);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long id) {
        chatRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllChats() {
        chatRepository.deleteAll();
        return ResponseEntity.ok().build();
    }
}
