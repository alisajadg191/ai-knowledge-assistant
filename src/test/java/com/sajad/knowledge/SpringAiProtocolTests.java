package com.sajad.knowledge;

import com.sajad.knowledge.rag.AiClient;
import com.sajad.knowledge.store.KnowledgeStore;
import com.sajad.knowledge.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Real Spring AI HTTP clients against controlled Ollama responses; no real inference. */
@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration","spring.sql.init.mode=never"})
class SpringAiProtocolTests {
    static final AtomicReference<String> kind=new AtomicReference<>("valid");
    static final AtomicReference<String> embeddingRequest=new AtomicReference<>("");
    static final AtomicReference<String> chatRequest=new AtomicReference<>("");
    static final HttpServer server=start();
    @Autowired AiClient ai;
    @MockitoBean KnowledgeStore store;
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.ai.ollama.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
    }
    static HttpServer start() {
        try {
            var s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            s.createContext("/api/embed",exchange->{
                embeddingRequest.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String vector="[1"+",0".repeat(767)+"]";
                byte[] data=("{\"model\":\"nomic-embed-text\",\"embeddings\":["+vector+"]}").getBytes();
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);
                exchange.getResponseBody().write(data);exchange.close();
            });
            s.createContext("/api/chat",exchange->{
                chatRequest.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String content=kind.get().equals("invalid")?"not JSON":"{\\\"answerable\\\":true,\\\"answer\\\":\\\"Every 30 minutes [1].\\\",\\\"citations\\\":[1]}";
                byte[] data=("{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\",\"content\":\""+content+"\"},\"done\":true,\"done_reason\":\"stop\"}").getBytes();
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);
                exchange.getResponseBody().write(data);exchange.close();
            });
            s.start();return s;
        } catch(Exception e){throw new RuntimeException(e);}
    }
    @BeforeEach void reset(){kind.set("valid");}
    @AfterAll static void stop(){server.stop(0);}
    @Test void realEmbeddingClientSendsPrefixesAndReads768Dimensions() {
        assertEquals(768,ai.embedQuestion("When to update?").length);
        assertTrue(embeddingRequest.get().contains("search_query:"));
        assertEquals(768,ai.embedDocuments(List.of("Update every 30 minutes")).getFirst().length);
        assertTrue(embeddingRequest.get().contains("search_document:"));
    }
    @Test void structuredAnswerUsesContextAndBudget() {
        var draft=ai.answer("When to update?","[1] Update every 30 minutes.");
        assertTrue(draft.answerable());assertEquals(List.of(1),draft.citations());
        assertTrue(chatRequest.get().contains("700"));assertTrue(chatRequest.get().contains("Update every 30 minutes"));
    }
    @Test void malformedModelOutputIsExplicitFailure() {
        kind.set("invalid");
        assertEquals("INVALID_MODEL_RESPONSE",assertThrows(ApiException.class,()->ai.answer("When?","[1] Text")).code());
    }
}
