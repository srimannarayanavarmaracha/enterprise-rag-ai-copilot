package com.sriman.rag.web;

import com.sriman.rag.domain.KnowledgeDocument;
import com.sriman.rag.rag.DocumentIngestionService;
import com.sriman.rag.repository.KnowledgeDocumentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    public record TextDocumentRequest(@NotBlank String title, @NotBlank String content) {}

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;

    public DocumentController(
        KnowledgeDocumentRepository documentRepository,
        DocumentIngestionService ingestionService
    ) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    @GetMapping
    public List<KnowledgeDocument> listDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument ingestText(
        @Valid @RequestBody TextDocumentRequest request,
        Authentication authentication
    ) {
        String user = authentication != null ? authentication.getName() : "system";
        return ingestionService.ingestRawText(request.title(), request.content(), user);
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument uploadFile(
        @RequestParam("file") MultipartFile file,
        Authentication authentication
    ) throws IOException {
        String user = authentication != null ? authentication.getName() : "system";
        return ingestionService.ingestFile(file, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable Long id) {
        ingestionService.deleteDocument(id);
    }
}
