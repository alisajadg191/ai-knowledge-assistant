package com.sajad.knowledge.store;

import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.document.TextExtractor.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Repository
public class KnowledgeStore {
    public record DocumentInfo(UUID id, String name, int chunks, Instant createdAt) {}
    public record Hit(UUID chunkId, UUID documentId, String name, int ordinal, Integer page, String text, double score) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public KnowledgeStore(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }
    public List<DocumentInfo> list() {
        return jdbc.query("SELECT id,name,chunk_count,created_at FROM knowledge_document ORDER BY created_at DESC", (r,n) ->
            new DocumentInfo(r.getObject(1,UUID.class), r.getString(2), r.getInt(3), r.getTimestamp(4).toInstant()));
    }
    public void assertModel(String model) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM knowledge_document WHERE embedding_model <> ?", Integer.class, model);
        if (count != null && count > 0) throw new ApiException(409,"EMBEDDING_MODEL_CHANGED","Existing documents use a different embedding model. Restore that model or delete and re-upload the documents.");
    }
    public Optional<DocumentInfo> duplicate(String hash) {
        return jdbc.query("SELECT id,name,chunk_count,created_at FROM knowledge_document WHERE content_hash=?", (r,n) ->
            new DocumentInfo(r.getObject(1,UUID.class),r.getString(2),r.getInt(3),r.getTimestamp(4).toInstant()), hash).stream().findFirst();
    }
    public DocumentInfo save(String name, String hash, String model, List<Chunk> chunks, List<float[]> vectors, Runnable checkDeadline) {
        return tx.execute(status -> {
            checkDeadline.run();
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO knowledge_document(id,name,content_hash,embedding_model,chunk_count) VALUES (?,?,?,?,?)",id,name,hash,model,chunks.size());
            for (int i=0;i<chunks.size();i++) {
                Chunk c = chunks.get(i);
                jdbc.update("INSERT INTO knowledge_chunk(id,document_id,ordinal,page,content,embedding) VALUES (?,?,?,?,?,?::vector)",
                    UUID.randomUUID(),id,c.ordinal(),c.page(),c.text(),vector(vectors.get(i)));
            }
            checkDeadline.run();
            return duplicate(hash).orElseThrow();
        });
    }
    public void delete(UUID id) {
        if (jdbc.update("DELETE FROM knowledge_document WHERE id=?", id)==0)
            throw new ApiException(404,"DOCUMENT_NOT_FOUND","Document not found.");
    }
    public boolean exists(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM knowledge_document WHERE id=?)",Boolean.class,id)); }
    public List<Hit> search(float[] query, UUID documentId, double threshold) {
        String v = vector(query);
        String sql = "SELECT c.id,c.document_id,d.name,c.ordinal,c.page,c.content,1-(c.embedding <=> ?::vector) AS score FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id "
            + (documentId == null ? "" : "WHERE c.document_id=? ") + "ORDER BY c.embedding <=> ?::vector,c.id LIMIT 5";
        Object[] args = documentId == null ? new Object[]{v,v} : new Object[]{v,documentId,v};
        return jdbc.query(sql,(r,n)->new Hit(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getInt(4),r.getObject(5,Integer.class),r.getString(6),r.getDouble(7)),args)
            .stream().filter(hit->Double.isFinite(hit.score()) && hit.score() >= threshold).toList();
    }
    public static String vector(float[] values) {
        if (values == null || values.length != 768) throw new ApiException(502,"EMBEDDING_DIMENSION","Expected a 768-dimensional embedding. Use nomic-embed-text or a compatible model.");
        double norm=0;
        for(float v:values) { if(!Float.isFinite(v)) throw new ApiException(502,"INVALID_EMBEDDING","Model returned an invalid embedding."); norm+=v*v; }
        if(norm==0) throw new ApiException(502,"INVALID_EMBEDDING","Model returned an empty embedding.");
        return Arrays.toString(values);
    }
}
