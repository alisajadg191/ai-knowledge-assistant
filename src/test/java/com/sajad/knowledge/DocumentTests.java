package com.sajad.knowledge;

import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.config.WorkGate;
import com.sajad.knowledge.document.*;
import com.sajad.knowledge.rag.AiClient;
import com.sajad.knowledge.store.KnowledgeStore;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTests {
    KnowledgeStore store=mock(KnowledgeStore.class);
    AiClient ai=mock(AiClient.class);
    WorkGate gate=new WorkGate(5);
    DocumentService service=new DocumentService(new TextExtractor(),store,ai,gate,"nomic-embed-text");
    @AfterEach void close(){gate.close();}
    @Test void duplicateDoesNotReembed() {
        var doc=new KnowledgeStore.DocumentInfo(UUID.randomUUID(),"a.txt",1,Instant.now());
        when(store.duplicate(anyString())).thenReturn(Optional.of(doc));
        assertTrue(service.upload("a.txt","Example".getBytes()).duplicate());verifyNoInteractions(ai);
    }
    @Test void embeddingFailureDoesNotWriteDocument() {
        when(store.duplicate(anyString())).thenReturn(Optional.empty());when(store.list()).thenReturn(List.of());
        when(ai.embedDocuments(anyList())).thenThrow(new ApiException(502,"MODEL_UNAVAILABLE","offline"));
        assertThrows(ApiException.class,()->service.upload("a.txt","Example evidence".getBytes()));
        verify(store,never()).save(anyString(),anyString(),anyString(),anyList(),anyList(),any());
    }
    @Test void successfulIngestionStoresValidatedVectors() {
        when(store.duplicate(anyString())).thenReturn(Optional.empty());when(store.list()).thenReturn(List.of());
        float[] vector=new float[768];vector[0]=1;
        when(ai.embedDocuments(anyList())).thenReturn(List.of(vector));
        var doc=new KnowledgeStore.DocumentInfo(UUID.randomUUID(),"a.txt",1,Instant.now());
        when(store.save(anyString(),anyString(),anyString(),anyList(),anyList(),any())).thenReturn(doc);
        assertEquals(doc,service.upload("a.txt","Example evidence".getBytes()).document());
        verify(store).save(eq("a.txt"),anyString(),eq("nomic-embed-text"),anyList(),anyList(),any());
    }
}
