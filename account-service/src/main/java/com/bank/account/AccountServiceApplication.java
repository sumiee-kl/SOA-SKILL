package com.bank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Internal Account Service.
 *
 * Exposes ONLY internal routes under /api/v1/internal/accounts and listens on an
 * isolated port (8081, bound to 127.0.0.1 by default). It is never meant to be
 * called directly by clients — all traffic should arrive through the API Gateway.
 */
@SpringBootApplication
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
