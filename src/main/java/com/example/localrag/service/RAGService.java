package com.example.localrag.service;

import com.example.localrag.config.AppProperties;
import com.example.localrag.entity.ChatResponse;
import com.example.localrag.entity.KnowledgeDocument;
import com.example.localrag.repository.ChatResponseRepository;
import com.example.localrag.repository.EmbeddingVectorRepository;
import com.example.localrag.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RAGService {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "your", "you", "can",
            "are", "was", "were", "how", "what", "when", "where", "why", "who", "have",
            "has", "had", "our", "about", "into", "onto", "after", "before", "then",
            "than", "them", "they", "their", "will", "would", "could", "should", "does",
            "did", "not", "but", "get", "got", "its", "please", "help", "need"
    );

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
                appProperties.getRag().getTopK() * 3
        );

        Set<String> uniqueCleaned = nearestContents.stream()
                .map(this::sanitizeContext)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> ranked = rankByQuestionOverlap(question, new ArrayList<>(uniqueCleaned), 2);
        String context = ranked.stream()
                .limit(appProperties.getRag().getTopK())
                .collect(Collectors.joining("\n---\n"));

        String prompt = "You are a customer support assistant. " +
                "Use only the provided context. " +
                "If the context is missing, say you do not have enough information. " +
                "Do not output template placeholders, brackets, or fake contact details.\n\n" +
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

    private String sanitizeContext(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("\\{\\{[^}]+}}", "")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private List<String> rankByQuestionOverlap(String question, List<String> candidates, int minOverlap) {
        Set<String> qTokens = tokenize(question);
        if (qTokens.isEmpty()) {
            return candidates;
        }

        return candidates.stream()
                .filter(c -> overlapScore(qTokens, extractQuestionPart(c)) >= minOverlap)
                .sorted(Comparator.comparingInt((String c) -> overlapScore(qTokens, extractQuestionPart(c))).reversed())
                .collect(Collectors.toList());
    }

    private int overlapScore(Set<String> qTokens, String candidate) {
        Set<String> cTokens = tokenize(candidate);
        int hits = 0;
        for (String token : qTokens) {
            if (cTokens.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }

        return TOKEN_SPLIT.splitAsStream(text.toLowerCase())
                .filter(t -> t.length() > 2)
                .filter(t -> !STOP_WORDS.contains(t))
                .collect(Collectors.toSet());
    }

    private String extractQuestionPart(String candidate) {
        int qStart = candidate.indexOf("Q:");
        int aStart = candidate.indexOf("\nA:");
        if (qStart >= 0 && aStart > qStart) {
            return candidate.substring(qStart + 2, aStart).trim();
        }
        return candidate;
    }
}
