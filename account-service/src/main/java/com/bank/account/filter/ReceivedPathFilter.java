package com.bank.account.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Verification aid: logs the exact path the backend received and echoes it back in the
 * X-Received-Path response header. This proves the gateway rewrote /accounts/** into
 * /api/v1/internal/accounts/** before forwarding.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReceivedPathFilter extends OncePerRequestFilter {

    public static final String RECEIVED_PATH_HEADER = "X-Received-Path";
    private static final Logger log = LoggerFactory.getLogger(ReceivedPathFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String full = query == null ? path : path + "?" + query;

        log.info("Backend received {} {} | X-Forwarded-Prefix={} | X-Gateway-Route={} | X-Original-Path={}",
                request.getMethod(), full,
                request.getHeader("X-Forwarded-Prefix"),
                request.getHeader("X-Gateway-Route"),
                request.getHeader("X-Original-Path"));

        response.setHeader(RECEIVED_PATH_HEADER, path);
        chain.doFilter(request, response);
    }
}
