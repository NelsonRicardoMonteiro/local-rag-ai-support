package com.example.localrag.repository;

import com.example.localrag.entity.EmbeddingVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EmbeddingVectorRepository extends JpaRepository<EmbeddingVector, Long> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO embedding_vector (knowledge_document_id, embedding)
        VALUES (:documentId, CAST(:embedding AS vector))
        """, nativeQuery = true)
    void insertForDocument(@Param("documentId") Long documentId, @Param("embedding") String embedding);

    @Query(value = """
        SELECT kd.content
        FROM knowledge_document kd
        JOIN embedding_vector ev ON ev.knowledge_document_id = kd.id
        ORDER BY ev.embedding <-> CAST(:queryEmbedding AS vector)
        LIMIT :k
        """, nativeQuery = true)
    List<String> findTopKContents(@Param("queryEmbedding") String queryEmbedding, @Param("k") int k);
}
