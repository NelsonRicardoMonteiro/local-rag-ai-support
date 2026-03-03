package com.example.localrag.controller;

import com.example.localrag.entity.ChatResponse;
import com.example.localrag.service.RAGService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RAGService ragService;

    public ChatController(RAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ChatResponse ask(@RequestBody AskRequest request) {
        return ragService.ask(request.question());
    }

    public record AskRequest(String question) {
    }
}
