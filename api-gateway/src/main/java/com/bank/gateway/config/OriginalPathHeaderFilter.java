package com.bank.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs FIRST (before RewritePath / StripPrefix) and records the path the client originally
 * called in an X-Original-Path header, so the backend can log "client called X, I received Y".
 */
@Component
public class OriginalPathHeaderFilter implements GlobalFilter, Ordered {

    public static final String ORIGINAL_PATH_HEADER = "X-Original-Path";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String originalPath = exchange.getRequest().getURI().getRawPath();
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(ORIGINAL_PATH_HEADER, originalPath)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
