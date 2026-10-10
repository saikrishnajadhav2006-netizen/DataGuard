package com.dataguard.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroqChatProviderTest {
    @Test
    void sendsOpenAiCompatibleRequestToGroqEndpointAndReturnsActualResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            assertEquals("Bearer test-provider-key", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).contains("test-model"));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"Use parameterized database queries.\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            GroqChatProvider provider = new GroqChatProvider(RestClient.builder(), base, "test-provider-key", "test-model", "groq");
            assertEquals("Use parameterized database queries.",
                    provider.complete(List.of(new RadarChatProvider.Turn("user", "How do I secure SQL?"))));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingKeyAndModelAreReportedInsteadOfReturningPreviewText() {
        GroqChatProvider provider = new GroqChatProvider(RestClient.builder(), "https://api.groq.com/openai/v1", "", "", "groq");
        AIProviderException error = assertThrows(AIProviderException.class,
                () -> provider.complete(List.of(new RadarChatProvider.Turn("user", "Recommend tools"))));
        assertEquals(503, error.getStatus());
        assertTrue(error.getMessage().contains("GROQ_API_KEY"));
    }
}
