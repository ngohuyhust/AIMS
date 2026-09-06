package vn.aims.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/** Only authorized module routes are opened; JWT/admin security is a later checkpoint. */
@Configuration(proxyBeanMethods = false)
public class FoundationSecurityConfiguration {
    @Bean
    SecurityFilterChain foundationSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/products/audit-logs", "/api/products/audit-logs/").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/", "/api/products/*", "/api/products/*/").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/products", "/api/products/", "/api/products/*", "/api/products/*/").permitAll()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
    }
}
