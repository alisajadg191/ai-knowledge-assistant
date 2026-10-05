package com.sajad.knowledge.document;

import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.config.WorkGate;
import com.sajad.knowledge.rag.AiClient;
import com.sajad.knowledge.store.KnowledgeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.*;

@Service
public class DocumentService {
    public record UploadResult(KnowledgeStore.DocumentInfo document, boolean duplicate) {}
    private final TextExtractor extractor;
    private final KnowledgeStore store;
    private final AiClient ai;
    private final WorkGate gate;
    private final String model;
    public DocumentService(TextExtractor extractor, KnowledgeStore store, AiClient ai, WorkGate gate, @Value("${app.embedding-model}") String model) {
        this.extractor=extractor; this.store=store; this.ai=ai; this.gate=gate; this.model=model;
    }
    public UploadResult upload(String original, byte[] bytes) {
        String name=extractor.safeName(original);
        return gate.run(deadline -> {
            store.assertModel(model);
            String hash;
            try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
            catch(Exception e) { throw new IllegalStateException(e); }
            var duplicate=store.duplicate(hash);
            if(duplicate.isPresent()) return new UploadResult(duplicate.get(),true);
            if(store.list().size() >= 50) throw new ApiException(409,"CORPUS_LIMIT","This local workspace supports up to 50 documents. Delete a document before adding more.");
            var chunks=extractor.extract(name,bytes);
            var vectors=new ArrayList<float[]>();
            for(int i=0;i<chunks.size();i+=8) {
                deadline.check();
                var batch=chunks.subList(i,Math.min(i+8,chunks.size()));
                var output=ai.embedDocuments(batch.stream().map(TextExtractor.Chunk::text).toList());
                if(output==null || output.size()!=batch.size()) throw new ApiException(502,"INVALID_EMBEDDING","Model returned the wrong number of embeddings.");
                output.forEach(KnowledgeStore::vector); vectors.addAll(output);
            }
            deadline.check();
            return new UploadResult(store.save(name,hash,model,chunks,vectors,deadline::check),false);
        });
    }
}
