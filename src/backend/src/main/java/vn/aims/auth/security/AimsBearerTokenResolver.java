package vn.aims.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

/** Resolves user JWTs only where they are required or explicitly supported. */
@Component
public final class AimsBearerTokenResolver implements BearerTokenResolver {
    private final boolean vietqrTestEnabled;

    public AimsBearerTokenResolver(@Value("${VIETQR_ENABLE_TEST_CALLBACK:false}") boolean vietqrTestEnabled) {
        this.vietqrTestEnabled = vietqrTestEnabled;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (!supports(request)) return null;
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public boolean supports(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.matches("/api/auth/change-password/?") || path.equals("/api/users")
                || path.startsWith("/api/users/") || path.startsWith("/api/auth/reset-password/")) return true;
        if (path.matches("/api/orders/(pending|vietqr-refunds)/?")
                || path.matches("/api/orders/[^/]+/(approve|cancel|reject|confirm-vietqr-refund)/?")) return true;
        if (path.equals("/api/products/audit-logs") || path.equals("/api/products/audit-logs/")) return true;
        if ((path.equals("/api/products") || path.startsWith("/api/products/"))
                && !method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) return true;
        if (method.equals("POST") && path.matches("/api/paypal/order/refund/?")) return true;
        return vietqrTestEnabled && method.equals("POST")
                && path.matches("/api/vietqr/payments/[^/]+/trigger-callback/?");
    }
}
