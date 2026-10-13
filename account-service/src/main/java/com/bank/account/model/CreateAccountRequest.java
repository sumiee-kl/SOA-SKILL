package com.bank.account.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String holderName,
        @NotNull AccountType type,
        @NotNull @PositiveOrZero BigDecimal openingBalance,
        @NotBlank String currency) {
}
