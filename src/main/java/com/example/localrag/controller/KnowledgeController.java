package com.example.localrag.controller;

import com.example.localrag.entity.KnowledgeDocument;
import com.example.localrag.service.RAGService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final RAGService ragService;

    public KnowledgeController(RAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument ingest(@RequestBody IngestRequest request) {
        return ragService.ingest(request.title(), request.content());
    }

    public record IngestRequest(String title, String content) {
    }
}
