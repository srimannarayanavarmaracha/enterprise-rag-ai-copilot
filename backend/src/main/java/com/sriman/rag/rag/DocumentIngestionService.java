package com.sriman.rag.rag;

import com.sriman.rag.domain.DocumentChunk;
import com.sriman.rag.domain.KnowledgeDocument;
import com.sriman.rag.repository.DocumentChunkRepository;
import com.sriman.rag.repository.KnowledgeDocumentRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TextChunker chunker;
    private final HybridRetrievalService retrievalService;

    public DocumentIngestionService(
        KnowledgeDocumentRepository documentRepository,
        DocumentChunkRepository chunkRepository,
        TextChunker chunker,
        HybridRetrievalService retrievalService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.retrievalService = retrievalService;
    }

    @Transactional
    public KnowledgeDocument ingestRawText(String title, String content, String uploadedBy) {
        return persistDocument(title, "text/plain", content, uploadedBy);
    }

    @Transactional
    public KnowledgeDocument ingestFile(MultipartFile file, String uploadedBy) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.txt";
        String contentType = file.getContentType() != null ? file.getContentType() : "text/plain";
        String extractedText;

        if (filename.toLowerCase().endsWith(".pdf")) {
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                new ByteArrayResource(file.getBytes())
            );
            List<Document> pages = pdfReader.get();
            extractedText = pages.stream()
                .map(Document::getText)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        } else {
            extractedText = new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        return persistDocument(filename, contentType, extractedText, uploadedBy);
    }

    @Transactional
    public void deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        chunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }

    private KnowledgeDocument persistDocument(String title, String contentType, String text, String uploadedBy) {
        List<String> chunks = chunker.chunk(text, 120, 25);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document contains no extractable text");
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(title);
        doc.setContentType(contentType);
        doc.setChunkCount(chunks.size());
        doc.setUploadedBy(uploadedBy);
        KnowledgeDocument savedDoc = documentRepository.save(doc);

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] vector = retrievalService.embed(chunkText);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(savedDoc);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setEmbeddingCsv(retrievalService.serializeVector(vector));
            chunkRepository.save(chunk);
        }

        return savedDoc;
    }
}
