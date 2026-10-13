package com.bank.account.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Account(
        String id,
        String holderName,
        AccountType type,
        BigDecimal balance,
        String currency,
        Instant createdAt) {
}
