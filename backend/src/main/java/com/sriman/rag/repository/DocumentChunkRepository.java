package com.sriman.rag.repository;

import com.sriman.rag.domain.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document")
    List<DocumentChunk> findAllWithDocument();

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);
    void deleteByDocumentId(Long documentId);
}
