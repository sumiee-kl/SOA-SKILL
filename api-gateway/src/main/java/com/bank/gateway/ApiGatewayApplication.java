package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Public API Gateway (port 8080).
 *
 * Clients call clean public paths such as /accounts/**; the gateway rewrites them to the
 * internal backend paths /api/v1/internal/accounts/** and forwards to the isolated
 * account-service (port 8081). Route definitions live in application.yml.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
