# Initial Testing Guide (Windows PowerShell)

## 1) Prerequisites
- Docker Desktop running
- Ollama running locally on `http://localhost:11434`
- Model pulled in Ollama:
  - `ollama pull llama3`
  - `ollama pull nomic-embed-text`
- Java 17+ and Maven available

## 2) Start PostgreSQL + pgvector
```powershell
docker compose up -d postgres
docker compose ps
```

Expected: container `rag-postgres` in `running` state.

## 3) Verify pgvector extension
```powershell
docker exec -it rag-postgres psql -U raguser -d ragdb -c "SELECT extname FROM pg_extension WHERE extname='vector';"
```

Expected: one row with `vector`.

## 4) Start Spring Boot API
From project root:
```powershell
mvn spring-boot:run
```

Expected: app listening on `http://localhost:8080`.

## 5) Health check
```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
```

If Actuator is not added yet, skip this and go directly to ingest endpoint tests.

## 6) Ingest a knowledge document
```powershell
$body = @{
  title = "Refund Policy"
  content = "Customers can request a refund within 30 days with proof of purchase."
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/knowledge/ingest `
  -ContentType "application/json" `
  -Body $body
```

Expected: JSON response with `id`, `title`, `content`, `createdAt`.

## 7) Ask a question
```powershell
$question = @{
  question = "How many days do I have to request a refund?"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/ask `
  -ContentType "application/json" `
  -Body $question
```

Expected: JSON response including `answer` and `contextUsed` matching ingested content.

## 8) Validate database records
```powershell
docker exec -it rag-postgres psql -U raguser -d ragdb -c "SELECT id, title, created_at FROM knowledge_document;"
docker exec -it rag-postgres psql -U raguser -d ragdb -c "SELECT id, knowledge_document_id FROM embedding_vector;"
docker exec -it rag-postgres psql -U raguser -d ragdb -c "SELECT id, question, created_at FROM chat_response;"
```

## 9) Common issues
- `Connection refused` to Ollama: start Ollama app/service first.
- Model not found: run `ollama pull llama3` and `ollama pull nomic-embed-text`.
- Docker pipe error on Windows: open Docker Desktop and wait until engine is fully started.
- Maven `.m2` permission issue: configure local repo in user environment and rerun.
