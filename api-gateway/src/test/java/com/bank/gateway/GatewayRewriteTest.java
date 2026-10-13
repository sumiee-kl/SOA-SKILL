package com.bank.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies forwarding end-to-end: a fake backend records the exact path + headers it received,
 * so we can assert the gateway rewrote /accounts/** into /api/v1/internal/accounts/**.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRewriteTest {

    static final AtomicReference<String> receivedPath = new AtomicReference<>();
    static final AtomicReference<String> receivedQuery = new AtomicReference<>();
    static final AtomicReference<String> receivedRoute = new AtomicReference<>();
    static final AtomicReference<String> receivedOriginal = new AtomicReference<>();
    static final HttpServer backend = startBackend();

    static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                receivedPath.set(exchange.getRequestURI().getRawPath());
                receivedQuery.set(exchange.getRequestURI().getRawQuery());
                receivedRoute.set(exchange.getRequestHeaders().getFirst("X-Gateway-Route"));
                receivedOriginal.set(exchange.getRequestHeaders().getFirst("X-Original-Path"));
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("X-Received-Path", exchange.getRequestURI().getRawPath());
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("ACCOUNT_SERVICE_URL", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        backend.stop(0);
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void reset() {
        receivedPath.set(null);
        receivedQuery.set(null);
        receivedRoute.set(null);
        receivedOriginal.set(null);
    }

    @Test
    void rewritesCollectionPath() {
        client.get().uri("/accounts").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Received-Path", "/api/v1/internal/accounts")
                .expectHeader().valueEquals("X-Gateway", "banking-api-gateway");

        assertThat(receivedPath.get()).isEqualTo("/api/v1/internal/accounts");
        assertThat(receivedRoute.get()).isEqualTo("accounts-rewrite");
        assertThat(receivedOriginal.get()).isEqualTo("/accounts");
    }

    @Test
    void rewritesNestedPathAndKeepsQueryString() {
        client.get().uri("/accounts/ACC1001?view=summary").exchange().expectStatus().isOk();

        assertThat(receivedPath.get()).isEqualTo("/api/v1/internal/accounts/ACC1001");
        assertThat(receivedQuery.get()).isEqualTo("view=summary");
    }

    @Test
    void rewritesPostRequests() {
        client.post().uri("/accounts").bodyValue("{}").exchange().expectStatus().isOk();

        assertThat(receivedPath.get()).isEqualTo("/api/v1/internal/accounts");
    }

    @Test
    void stripPrefixRemovesFirstSegment() {
        client.get().uri("/account-service/api/v1/internal/accounts/ACC1002").exchange()
                .expectStatus().isOk();

        assertThat(receivedPath.get()).isEqualTo("/api/v1/internal/accounts/ACC1002");
        assertThat(receivedRoute.get()).isEqualTo("accounts-strip-prefix");
    }

    @Test
    void directInternalPathIsBlockedAtTheEdge() {
        client.get().uri("/api/v1/internal/accounts").exchange()
                .expectStatus().isForbidden();

        assertThat(receivedPath.get()).isNull(); // never reached the backend
    }

    @Test
    void unknownPathIsNotRouted() {
        client.get().uri("/payments").exchange().expectStatus().isNotFound();
        assertThat(receivedPath.get()).isNull();
    }
}
