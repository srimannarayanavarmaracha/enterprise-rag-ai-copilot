package com.sriman.rag.web;

import com.sriman.rag.rag.HybridRetrievalService;
import com.sriman.rag.rag.HybridRetrievalService.ScoredChunk;
import com.sriman.rag.rag.LlmSynthesisService;
import com.sriman.rag.rag.LlmSynthesisService.RagAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    public record AskRequest(
        @NotBlank String question,
        @Min(1) @Max(10) Integer topK
    ) {}

    private final HybridRetrievalService retrievalService;
    private final LlmSynthesisService synthesisService;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(HybridRetrievalService retrievalService, LlmSynthesisService synthesisService) {
        this.retrievalService = retrievalService;
        this.synthesisService = synthesisService;
    }

    @PostMapping("/ask")
    public RagAnswer ask(@Valid @RequestBody AskRequest request) {
        int k = request.topK() != null ? request.topK() : 3;
        long start = System.nanoTime();
        List<ScoredChunk> chunks = retrievalService.retrieveTopK(request.question(), k);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return synthesisService.synthesize(request.question(), chunks, latencyMs);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String question, @RequestParam(defaultValue = "3") int topK) {
        SseEmitter emitter = new SseEmitter(60_000L);
        streamExecutor.submit(() -> {
            try {
                long start = System.nanoTime();
                List<ScoredChunk> chunks = retrievalService.retrieveTopK(question, Math.min(10, Math.max(1, topK)));
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                RagAnswer answer = synthesisService.synthesize(question, chunks, latencyMs);

                emitter.send(SseEmitter.event().name("citations").data(answer.citations()));
                String[] tokens = answer.answer().split("(?<=\\s)");
                for (String token : tokens) {
                    emitter.send(SseEmitter.event().name("token").data(token));
                    Thread.sleep(15);
                }
                emitter.send(SseEmitter.event().name("done").data(
                    java.util.Map.of(
                        "provider", answer.provider(),
                        "retrievalLatencyMs", answer.retrievalLatencyMs(),
                        "topMatchScore", answer.topMatchScore()
                    )
                ));
                emitter.complete();
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }
}
