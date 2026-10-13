package com.bank.account.service;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import com.bank.account.model.CreateAccountRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Simple in-memory store so the demo runs with zero external dependencies. */
@Service
public class AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000);

    public AccountService() {
        save(new CreateAccountRequest("Asha Verma", AccountType.SAVINGS, new BigDecimal("25000.00"), "INR"));
        save(new CreateAccountRequest("Rahul Mehta", AccountType.CURRENT, new BigDecimal("150000.00"), "INR"));
    }

    public List<Account> findAll() {
        Collection<Account> values = accounts.values();
        return values.stream().sorted(Comparator.comparing(Account::id)).toList();
    }

    public Optional<Account> findById(String id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Account save(CreateAccountRequest request) {
        String id = "ACC" + sequence.incrementAndGet();
        Account account = new Account(id, request.holderName(), request.type(),
                request.openingBalance(), request.currency(), Instant.now());
        accounts.put(id, account);
        return account;
    }
}
