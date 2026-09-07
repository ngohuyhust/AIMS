package vn.aims.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Global legacy CORS; no-store applies to API routes. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyHttpFilter extends OncePerRequestFilter {
    private final Set<String> configuredOrigins;
    public LegacyHttpFilter(@Value("${ALLOWED_ORIGINS:}") String origins) {
        configuredOrigins=Arrays.stream(origins.split(",")).map(s -> s.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", ""))
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/api") || request.getRequestURI().startsWith("/api/")) response.setHeader("Cache-Control","no-store");
        String origin=request.getHeader("Origin");
        boolean allowed=origin == null || origin.matches("^http://(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):[0-9]+$")
                || origin.matches("^https://.*\\.vercel\\.app$") || configuredOrigins.contains(origin);
        if (allowed) {
            response.addHeader("Vary","Origin");
            if (origin != null) response.setHeader("Access-Control-Allow-Origin",origin);
            if (request.getMethod().equals("OPTIONS")) {
                response.setHeader("Access-Control-Allow-Methods","GET,HEAD,PUT,PATCH,POST,DELETE");
                response.addHeader("Vary","Access-Control-Request-Headers");
                String headers=request.getHeader("Access-Control-Request-Headers");
                if (headers != null) response.setHeader("Access-Control-Allow-Headers",headers);
                response.setStatus(204);
                response.setContentLength(0);
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
