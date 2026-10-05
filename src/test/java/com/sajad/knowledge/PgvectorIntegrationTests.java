package com.sajad.knowledge;

import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.document.DocumentService;
import com.sajad.knowledge.document.TextExtractor.Chunk;
import com.sajad.knowledge.rag.*;
import com.sajad.knowledge.store.KnowledgeStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL/pgvector, deterministic mocked model. Uses a dedicated test database. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_PGVECTOR_TESTS",matches="true")
class PgvectorIntegrationTests {
    @Autowired KnowledgeStore store;
    @Autowired DocumentService documents;
    @Autowired RagService rag;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean AiClient ai;
    float[] vector(){float[] v=new float[768];v[0]=1;return v;}
    @BeforeEach void setup(){
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).endsWith("_test"), "Integration tests require a disposable database ending in _test");
        jdbc.update("DELETE FROM knowledge_document");
        when(ai.embedDocuments(anyList())).thenAnswer(inv->{List<String> texts=inv.getArgument(0);return texts.stream().map(t->vector()).toList();});
        when(ai.embedQuestion(anyString())).thenReturn(vector());
        when(ai.answer(anyString(),anyString())).thenReturn(new AiClient.Draft(true,"Updates are every 30 minutes [1].",List.of(1)));
    }
    @AfterEach void clean(){if(jdbc.queryForObject("SELECT current_database()",String.class).endsWith("_test"))jdbc.update("DELETE FROM knowledge_document");}
    @Test void uploadAskDuplicateAndCascadeDelete() throws Exception {
        byte[] text="Post incident updates every 30 minutes.".getBytes();
        var uploaded=documents.upload("runbook.md",text);
        assertFalse(uploaded.duplicate());assertTrue(documents.upload("copy.md",text).duplicate());assertEquals(1,store.list().size());
        var answer=rag.ask("When should updates happen?",uploaded.document().id(),"answer");
        assertEquals("ANSWERED",answer.status());assertEquals(1,answer.citations().getFirst());
        assertEquals("runbook.md",answer.sources().getFirst().filename());assertEquals(1,answer.sources().getFirst().similarity(),.0001);
        mvc.perform(delete("/api/documents/"+uploaded.document().id())).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk",Integer.class));
    }
    @Test void filteringAndThresholdExcludeOtherDocuments() {
        var a=documents.upload("a.txt","First document".getBytes()).document();
        var b=documents.upload("b.txt","Second document".getBytes()).document();
        assertEquals(1,store.search(vector(),a.id(),.45).size());
        assertEquals(a.id(),store.search(vector(),a.id(),.45).getFirst().documentId());
        float[] unrelated=new float[768];unrelated[1]=1;assertTrue(store.search(unrelated,null,.45).isEmpty());
    }
    @Test void failedSaveRollsBackAllRows() {
        assertThrows(ApiException.class,()->store.save("broken.txt","bad-hash","nomic-embed-text",List.of(new Chunk(1,null,"A"),new Chunk(2,null,"B")),List.of(vector(),new float[2]),()->{}));
        assertTrue(store.list().isEmpty());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk",Integer.class));
    }
    @Test void changedModelIsRejected() {
        documents.upload("notes.txt","Some knowledge".getBytes());
        assertEquals(409,assertThrows(ApiException.class,()->store.assertModel("another-model")).status());
    }
    @Test void httpValidationAndMultipartUpload() throws Exception {
        mvc.perform(post("/api/documents")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").contentType("application/json").content("{\"question\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file","guide.txt","text/plain","Post updates every 30 minutes.".getBytes())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.document.chunks").value(1));
        mvc.perform(get("/api/documents")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("guide.txt"));
    }
}
