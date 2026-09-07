package vn.aims.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/** Open only authorized routes; shared method security uses exact role authorities. */
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class FoundationSecurityConfiguration {
    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new vn.aims.auth.LegacyBcryptPasswordEncoder();
    }
    @Bean
    SecurityFilterChain foundationSecurity(HttpSecurity http, vn.aims.auth.JwtTokens tokens,
            com.fasterxml.jackson.databind.ObjectMapper json) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/login", "/api/auth/login/", "/api/auth/change-password", "/api/auth/change-password/"))
                .addFilterBefore(new vn.aims.auth.JwtAuthenticationFilter(tokens,json), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST,"/api/auth/login", "/api/auth/login/").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/auth/change-password", "/api/auth/change-password/").authenticated()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/products/audit-logs", "/api/products/audit-logs/").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/", "/api/products/*", "/api/products/*/").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/products", "/api/products/", "/api/products/*", "/api/products/*/").permitAll()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN))
                        .accessDeniedHandler((request,response,error) -> {
                            response.setStatus(403); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
                            json.writeValue(response.getWriter(),vn.aims.auth.AuthErrorHandler.body(403,"Bạn không có quyền truy cập chức năng này"));
                        }))
                .build();
    }
}
