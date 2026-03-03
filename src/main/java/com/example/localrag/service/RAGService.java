package com.example.localrag.service;

import com.example.localrag.config.AppProperties;
import com.example.localrag.entity.ChatResponse;
import com.example.localrag.entity.KnowledgeDocument;
import com.example.localrag.repository.ChatResponseRepository;
import com.example.localrag.repository.EmbeddingVectorRepository;
import com.example.localrag.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RAGService {

    private final KnowledgeDocumentRepository knowledgeRepository;
    private final EmbeddingVectorRepository embeddingVectorRepository;
    private final ChatResponseRepository chatResponseRepository;
    private final AppProperties appProperties;
    private final RestClient ollamaClient;

    public RAGService(KnowledgeDocumentRepository knowledgeRepository,
                      EmbeddingVectorRepository embeddingVectorRepository,
                      ChatResponseRepository chatResponseRepository,
                      AppProperties appProperties) {
        this.knowledgeRepository = knowledgeRepository;
        this.embeddingVectorRepository = embeddingVectorRepository;
        this.chatResponseRepository = chatResponseRepository;
        this.appProperties = appProperties;
        this.ollamaClient = RestClient.builder()
                .baseUrl(appProperties.getOllama().getBaseUrl())
                .build();
    }

    public KnowledgeDocument ingest(String title, String content) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title);
        document.setContent(content);
        KnowledgeDocument savedDoc = knowledgeRepository.save(document);

        String embedding = generateEmbedding(content);
        embeddingVectorRepository.insertForDocument(savedDoc.getId(), embedding);

        return savedDoc;
    }

    public ChatResponse ask(String question) {
        String questionEmbedding = generateEmbedding(question);
        List<String> nearestContents = embeddingVectorRepository.findTopKContents(
                questionEmbedding,
                appProperties.getRag().getTopK()
        );

        String context = nearestContents.stream()
                .collect(Collectors.joining("\n---\n"));

        String prompt = "Use only this context to answer the user question. " +
                "If context is missing, say you do not have enough information.\n\n" +
                "Context:\n" + context + "\n\n" +
                "Question: " + question;

        Map<String, Object> request = Map.of(
                "model", appProperties.getOllama().getModel(),
                "prompt", prompt,
                "stream", false
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = ollamaClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(Map.class);

        String answer = response == null
                ? "No response from model"
                : String.valueOf(response.getOrDefault("response", "No answer"));

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setQuestion(question);
        chatResponse.setAnswer(answer);
        chatResponse.setContextUsed(context);
        return chatResponseRepository.save(chatResponse);
    }

    private String generateEmbedding(String text) {
        Map<String, Object> request = Map.of(
                "model", appProperties.getOllama().getEmbeddingModel(),
                "prompt", text
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = ollamaClient.post()
                .uri("/api/embeddings")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("embedding") == null) {
            throw new IllegalStateException("Ollama embedding API returned empty response");
        }

        @SuppressWarnings("unchecked")
        List<Number> embedding = (List<Number>) response.get("embedding");
        String values = embedding.stream()
                .map(Number::toString)
                .collect(Collectors.joining(","));

        return "[" + values + "]";
    }
}
