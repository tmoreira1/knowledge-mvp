package com.knowledge.repository.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the X-Actor header (default "system") into ActorContext for the
 * duration of the request, then clears it.
 */
@Component
@Order(1)
public class ActorFilter extends OncePerRequestFilter {

    public static final String ACTOR_HEADER = "X-Actor";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            ActorContext.set(request.getHeader(ACTOR_HEADER));
            filterChain.doFilter(request, response);
        } finally {
            ActorContext.clear();
        }
    }
}
