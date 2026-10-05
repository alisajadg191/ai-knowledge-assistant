package com.sajad.knowledge;

import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.config.WorkGate;
import com.sajad.knowledge.rag.*;
import com.sajad.knowledge.store.KnowledgeStore;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagTests {
    KnowledgeStore store=mock(KnowledgeStore.class);
    AiClient ai=mock(AiClient.class);
    WorkGate gate=new WorkGate(5);
    RagService rag=new RagService(store,ai,gate,"nomic-embed-text",.45);
    @AfterEach void close(){gate.close();}
    @Test void emptyLibraryDeclinesWithoutModelCalls() {
        when(store.list()).thenReturn(List.of());
        assertEquals("NO_EVIDENCE",rag.ask("What happens?",null,"answer").status());
        verifyNoInteractions(ai);
    }
    void evidence() {
        UUID id=UUID.randomUUID();
        when(store.list()).thenReturn(List.of(new KnowledgeStore.DocumentInfo(id,"runbook.md",1,Instant.now())));
        when(ai.embedQuestion(anyString())).thenReturn(new float[768]);
        when(store.search(any(),isNull(),eq(.45))).thenReturn(List.of(new KnowledgeStore.Hit(UUID.randomUUID(),id,"runbook.md",1,null,"Post updates every 30 minutes.",.8)));
    }
    @Test void sourcesOnlyRetrievesWithoutGenerating() {
        evidence();var answer=rag.ask("When to update?",null,"sources");
        assertEquals("SOURCES_ONLY",answer.status());assertEquals(1,answer.sources().size());
        verify(ai,never()).answer(anyString(),anyString());
    }
    @Test void noMatchingPassagesSkipsGeneration() {
        evidence();when(store.search(any(),isNull(),eq(.45))).thenReturn(List.of());
        assertEquals("NO_EVIDENCE",rag.ask("Unrelated question",null,"answer").status());
        verify(ai,never()).answer(anyString(),anyString());
    }
    @Test void keepsExactEvidenceAndValidatesCitationReferences() {
        evidence();when(ai.answer(anyString(),anyString())).thenReturn(new AiClient.Draft(true,"Every 30 minutes [1].",List.of(1)));
        var result=rag.ask("When to update?",null,"answer");
        assertEquals("ANSWERED",result.status());assertEquals("Post updates every 30 minutes.",result.sources().getFirst().excerpt());
        when(ai.answer(anyString(),anyString())).thenReturn(new AiClient.Draft(true,"Every 10 minutes [2].",List.of(2)));
        assertEquals("INVALID_CITATIONS",assertThrows(ApiException.class,()->rag.ask("When?",null,"answer")).code());
    }
    @Test void modelAbstentionUsesFixedNoEvidenceMessage() {
        evidence();when(ai.answer(anyString(),anyString())).thenReturn(new AiClient.Draft(false,"Untrusted unsupported wording",List.of(99)));
        assertEquals("NO_EVIDENCE",rag.ask("What salary?",null,"answer").status());
    }
    @Test void rejectsMissingInlineAndOutOfRangeCitations() {
        assertThrows(ApiException.class,()->RagService.validate(new AiClient.Draft(true,"Answer",List.of(1)),1));
        assertThrows(ApiException.class,()->RagService.validate(new AiClient.Draft(true,"Answer [2]",List.of(1)),2));
        assertThrows(ApiException.class,()->RagService.validate(new AiClient.Draft(true,"Answer [99999999999999999999]",List.of(1)),1));
        RagService.validate(new AiClient.Draft(true,"Answer [1]",List.of(1)),1);
    }
    @Test void rejectsBadQuestionsAndModelVectors() {
        assertEquals(400,assertThrows(ApiException.class,()->rag.ask(" ",null,"answer")).status());
        assertEquals(400,assertThrows(ApiException.class,()->rag.ask("Question",null,"bad")).status());
        assertThrows(ApiException.class,()->KnowledgeStore.vector(new float[3]));
        assertThrows(ApiException.class,()->KnowledgeStore.vector(new float[768]));
        float[] values=new float[768];values[0]=Float.NaN;
        assertThrows(ApiException.class,()->KnowledgeStore.vector(values));
    }
}
