package com.sajad.knowledge.rag;

import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.config.WorkGate;
import com.sajad.knowledge.store.KnowledgeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RagService {
    public record Source(int number, UUID documentId, String filename, int chunk, Integer page, double similarity, String excerpt) {}
    public record Answer(String id, String question, String status, String answer, List<Integer> citations,
                         List<Source> sources, String mode, long durationMs, Instant generatedAt, String warning) {}
    private final KnowledgeStore store;
    private final AiClient ai;
    private final WorkGate gate;
    private final String model;
    private final double threshold;
    public RagService(KnowledgeStore store,AiClient ai,WorkGate gate,@Value("${app.embedding-model}") String model,
                      @Value("${app.similarity-threshold:0.45}") double threshold) {
        this.store=store; this.ai=ai; this.gate=gate; this.model=model; this.threshold=threshold;
        if(!Double.isFinite(threshold)||threshold<0||threshold>1) throw new IllegalArgumentException("Similarity threshold must be 0–1");
    }
    public Answer ask(String question, UUID documentId, String mode) {
        if(question==null || question.isBlank() || question.length()>2000)
            throw new ApiException(400,"QUESTION","Question must contain 1–2000 characters.");
        if(!List.of("answer","sources").contains(mode)) throw new ApiException(400,"MODE","mode must be answer or sources.");
        long start=System.nanoTime();
        return gate.run(deadline -> {
            store.assertModel(model);
            if(documentId!=null && !store.exists(documentId)) throw new ApiException(404,"DOCUMENT_NOT_FOUND","Selected document no longer exists.");
            if(store.list().isEmpty()) return report(question,"NO_EVIDENCE","Upload a document before asking a question.",List.of(),List.of(),mode,start);
            var vector=ai.embedQuestion(question.trim()); deadline.check();
            var hits=store.search(vector,documentId,threshold);
            List<Source> sources=new ArrayList<>();
            for(var hit:hits) sources.add(new Source(sources.size()+1,hit.documentId(),hit.name(),hit.ordinal(),hit.page(),hit.score(),hit.text()));
            if(sources.isEmpty()) return report(question,"NO_EVIDENCE","I could not find sufficiently similar passages. Try a more specific question or another document.",List.of(),sources,mode,start);
            if(mode.equals("sources")) return report(question,"SOURCES_ONLY","Retrieved passages are shown below. No answer was generated.",List.of(),sources,mode,start);
            StringBuilder context=new StringBuilder();
            for(var s:sources) context.append("[").append(s.number()).append("] ").append(s.excerpt()).append("\n\n");
            var draft=ai.answer(question,context.toString()); deadline.check();
            if(draft==null) throw invalid();
            if(!draft.answerable()) return report(question,"NO_EVIDENCE","I could not find sufficient evidence in the retrieved passages.",List.of(),sources,mode,start);
            validate(draft,sources.size());
            return report(question,"ANSWERED",draft.answer(),List.copyOf(new LinkedHashSet<>(draft.citations())),sources,mode,start);
        });
    }
    public static void validate(AiClient.Draft draft,int count) {
        if(draft.answer()==null || draft.answer().isBlank() || draft.answer().length()>6000 || draft.citations()==null || draft.citations().isEmpty()) throw invalid();
        Set<Integer> cited=new HashSet<>(draft.citations());
        if(cited.stream().anyMatch(i->i==null || i<1 || i>count)) throw invalid();
        var matcher=Pattern.compile("\\[(\\d+)\\]").matcher(draft.answer());
        Set<Integer> inline=new HashSet<>();
        while(matcher.find()) { try { inline.add(Integer.valueOf(matcher.group(1))); } catch(NumberFormatException e) { throw invalid(); } }
        if(!inline.equals(cited)) throw invalid();
    }
    private static ApiException invalid() { return new ApiException(502,"INVALID_CITATIONS","The model returned missing or invalid citations. Try Sources only to inspect the evidence."); }
    private Answer report(String question,String status,String answer,List<Integer> citations,List<Source> sources,String mode,long start) {
        return new Answer(UUID.randomUUID().toString(),question,status,answer,citations,List.copyOf(sources),mode,
            (System.nanoTime()-start)/1_000_000,Instant.now(),"AI can misinterpret documents. Citations are checked for valid references, not factual support. Verify important claims against the excerpts. Similarity is not confidence.");
    }
}
