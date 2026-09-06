package vn.aims.product;

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

/** Only catalog HTTP compatibility; authentication/CORS for other features belongs to module 3. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogHttpFilter extends OncePerRequestFilter {
    private final Set<String> configuredOrigins;
    public CatalogHttpFilter(@Value("${ALLOWED_ORIGINS:}") String origins) {
        configuredOrigins=Arrays.stream(origins.split(",")).map(ProductSearch::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !(path.equals("/api/products") || path.startsWith("/api/products/"));
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control","no-store");
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
