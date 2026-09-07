package vn.aims.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installed only inside Spring Security, never as a second servlet filter. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String MISSING = "Không có quyền truy cập: Token thiếu hoặc không hợp lệ";
    public static final String INVALID = "Không có quyền truy cập: Phiên đăng nhập đã hết hạn hoặc không hợp lệ";
    private final JwtTokens tokens;
    private final ObjectMapper json;
    public JwtAuthenticationFilter(JwtTokens tokens, ObjectMapper json) { this.tokens=tokens; this.json=json; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !(path.matches("/api/auth/change-password/?") || path.equals("/api/users") || path.startsWith("/api/users/") || path.startsWith("/api/auth/reset-password/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header=request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) { reject(response,MISSING); return; }
        try {
            String[] pieces=header.split(" ",-1);
            var claims=tokens.verify(pieces[1]);
            var authorities=claims.getStringListClaim("roles").stream().map(SimpleGrantedAuthority::new).toList();
            var auth=UsernamePasswordAuthenticationToken.authenticated(claims.getIntegerClaim("userID"),null,authorities);
            auth.setDetails(claims.getStringClaim("email"));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception error) { reject(response,INVALID); return; }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(),AuthErrorHandler.body(401,message));
    }
}
