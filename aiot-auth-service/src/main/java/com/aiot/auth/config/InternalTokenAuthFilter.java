package com.aiot.auth.config;

import com.aiot.common.security.InternalTokenUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenAuthFilter.class);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String INTERNAL_PATH_PATTERN = "/api/v1/internal/**";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final int MIN_TOKEN_LENGTH = 24;

    @Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}")
    private String internalToken;

    @PostConstruct
    public void validateToken() {
        if (!StringUtils.hasText(internalToken) || internalToken.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "[FATAL] aiot.internal.token (AIOT_INTERNAL_TOKEN) must be configured, "
                            + "minLength=" + MIN_TOKEN_LENGTH + ", actual="
                            + (internalToken == null ? "null" : internalToken.length()));
        }
        log.info("InternalTokenAuthFilter initialized, token length={}", internalToken.length());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestUri = Objects.requireNonNull(request.getRequestURI());
        if (!PATH_MATCHER.match(INTERNAL_PATH_PATTERN, requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(requestToken) || !InternalTokenUtils.matches(internalToken, requestToken)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized internal request\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
