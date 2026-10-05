package com.sajad.knowledge.api;

import com.sajad.knowledge.config.WorkGate;
import com.sajad.knowledge.document.DocumentService;
import com.sajad.knowledge.rag.RagService;
import com.sajad.knowledge.store.KnowledgeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final KnowledgeStore store;
    private final DocumentService documents;
    private final RagService rag;
    private final WorkGate gate;
    private final String embeddingModel;
    private final String chatModel;
    public KnowledgeController(KnowledgeStore store,DocumentService documents,RagService rag,WorkGate gate,
        @Value("${app.embedding-model}") String embeddingModel,@Value("${spring.ai.ollama.chat.options.model}") String chatModel) {
        this.store=store;this.documents=documents;this.rag=rag;this.gate=gate;this.embeddingModel=embeddingModel;this.chatModel=chatModel;
    }
    @GetMapping("/status") public Map<String,Object> status() {
        return Map.of("status","UP","documents",store.list().size(),"busy",gate.busy(),"embeddingModel",embeddingModel,"chatModel",chatModel);
    }
    @GetMapping("/documents") public List<KnowledgeStore.DocumentInfo> list() { return store.list(); }
    @PostMapping("/documents") public DocumentService.UploadResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        return documents.upload(file.getOriginalFilename(),file.getBytes());
    }
    @DeleteMapping("/documents/{id}") public Map<String,String> delete(@PathVariable UUID id) {
        return gate.run(deadline -> { store.delete(id); return Map.of("status","deleted"); });
    }
    public record Question(String question,UUID documentId,String mode) {}
    @PostMapping("/questions") public RagService.Answer ask(@RequestBody Question request) {
        return rag.ask(request.question(),request.documentId(),request.mode()==null?"answer":request.mode());
    }
}
