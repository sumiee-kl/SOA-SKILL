package com.bank.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsAccountsOnInternalPath() throws Exception {
        mvc.perform(get("/api/v1/internal/accounts"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Received-Path", "/api/v1/internal/accounts"))
                .andExpect(jsonPath("$[0].id").value("ACC1001"));
    }

    @Test
    void getsAccountById() throws Exception {
        mvc.perform(get("/api/v1/internal/accounts/ACC1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Asha Verma"));
    }

    @Test
    void returns404ForUnknownAccount() throws Exception {
        mvc.perform(get("/api/v1/internal/accounts/NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void createsAccount() throws Exception {
        mvc.perform(post("/api/v1/internal/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"holderName":"Neha Kapoor","type":"SAVINGS","openingBalance":5000,"currency":"INR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void publicPathIsNotServedByBackend() throws Exception {
        // The backend only knows internal routes; /accounts must come through the gateway.
        mvc.perform(get("/accounts")).andExpect(status().isNotFound());
    }
}
