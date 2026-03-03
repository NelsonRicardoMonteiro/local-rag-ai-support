package com.example.localrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Ollama ollama = new Ollama();
    private final Rag rag = new Rag();

    public Ollama getOllama() {
        return ollama;
    }

    public Rag getRag() {
        return rag;
    }

    public static class Ollama {
        private String baseUrl;
        private String model;
        private String embeddingModel;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }

    public static class Rag {
        private int topK = 5;
        private int embeddingDim = 768;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getEmbeddingDim() {
            return embeddingDim;
        }

        public void setEmbeddingDim(int embeddingDim) {
            this.embeddingDim = embeddingDim;
        }
    }
}
