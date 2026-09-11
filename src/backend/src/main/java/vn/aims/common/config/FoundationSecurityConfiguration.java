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
        return new vn.aims.auth.security.LegacyBcryptPasswordEncoder();
    }
    @Bean
    SecurityFilterChain foundationSecurity(HttpSecurity http, vn.aims.auth.security.JwtTokens tokens,
            com.fasterxml.jackson.databind.ObjectMapper json) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/orders/*/approve", "/api/orders/*/approve/", "/api/orders/*/cancel", "/api/orders/*/cancel/", "/api/orders/*/reject", "/api/orders/*/reject/", "/api/orders/*/confirm-vietqr-refund", "/api/orders/*/confirm-vietqr-refund/", "/api/customer/orders/*/cancel", "/api/customer/orders/*/cancel/", "/api/vietqr/payments", "/api/vietqr/payments/", "/api/vietqr/payments/callback", "/api/vietqr/payments/callback/", "/api/vietqr/payments/*/trigger-callback", "/api/vietqr/payments/*/trigger-callback/", "/vqr/api/token_generate", "/vqr/api/token_generate/", "/vqr/bank/api/transaction-callback", "/vqr/bank/api/transaction-callback/", "/vqr/bank/api/transaction-sync", "/vqr/bank/api/transaction-sync/", "/api/paypal/order/create", "/api/paypal/order/create/", "/api/paypal/order/capture", "/api/paypal/order/capture/", "/api/paypal/order/refund", "/api/paypal/order/refund/", "/api/orders", "/api/orders/", "/api/orders/*/delivery-info", "/api/orders/*/delivery-info/", "/api/orders/cart/check-stock", "/api/orders/cart/check-stock/", "/api/orders/shipping-fee", "/api/orders/shipping-fee/", "/api/auth/login", "/api/auth/login/", "/api/auth/change-password", "/api/auth/change-password/", "/api/users", "/api/users/**", "/api/auth/reset-password/**", "/api/products", "/api/products/**"))
                .addFilterBefore(new vn.aims.auth.security.JwtAuthenticationFilter(tokens,json), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/vietqr/payments", "/api/vietqr/payments/", "/api/vietqr/payments/callback", "/api/vietqr/payments/callback/", "/api/vietqr/payments/*/trigger-callback", "/api/vietqr/payments/*/trigger-callback/", "/vqr/api/token_generate", "/vqr/api/token_generate/", "/vqr/bank/api/transaction-callback", "/vqr/bank/api/transaction-callback/", "/vqr/bank/api/transaction-sync", "/vqr/bank/api/transaction-sync/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vietqr/payments/*/status", "/api/vietqr/payments/*/status/", "/api/vietqr/payments/by-ref/*/status", "/api/vietqr/payments/by-ref/*/status/").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/vietqr/payments/*/status", "/api/vietqr/payments/*/status/", "/api/vietqr/payments/by-ref/*/status", "/api/vietqr/payments/by-ref/*/status/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/paypal/order/create", "/api/paypal/order/create/", "/api/paypal/order/capture", "/api/paypal/order/capture/", "/api/paypal/order/refund", "/api/paypal/order/refund/").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/users","/api/users/","/api/users/logs","/api/users/logs/").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.HEAD,"/api/users","/api/users/","/api/users/logs","/api/users/logs/").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/users","/api/users/","/api/users/*/reset-password","/api/users/*/reset-password/","/api/auth/reset-password/*","/api/auth/reset-password/*/").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/users/*","/api/users/*/","/api/users/*/status","/api/users/*/status/","/api/users/*/roles","/api/users/*/roles/").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/orders/pending","/api/orders/pending/","/api/orders/vietqr-refunds","/api/orders/vietqr-refunds/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.HEAD,"/api/orders/pending","/api/orders/pending/","/api/orders/vietqr-refunds","/api/orders/vietqr-refunds/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/approve", "/api/orders/*/approve/", "/api/orders/*/cancel", "/api/orders/*/cancel/", "/api/orders/*/reject", "/api/orders/*/reject/", "/api/orders/*/confirm-vietqr-refund", "/api/orders/*/confirm-vietqr-refund/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/customer/orders/*/cancel", "/api/customer/orders/*/cancel/").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/orders","/api/orders/").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/orders/*","/api/orders/*/","/api/customer/orders/*","/api/customer/orders/*/").permitAll()
                        .requestMatchers(HttpMethod.HEAD,"/api/orders/*","/api/orders/*/","/api/customer/orders/*","/api/customer/orders/*/").permitAll()
                        .requestMatchers(HttpMethod.PATCH,"/api/orders/*/delivery-info","/api/orders/*/delivery-info/").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/orders/cart/check-stock", "/api/orders/cart/check-stock/", "/api/orders/shipping-fee", "/api/orders/shipping-fee/").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/auth/login", "/api/auth/login/").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/auth/change-password", "/api/auth/change-password/").authenticated()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/products/audit-logs", "/api/products/audit-logs/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.HEAD,"/api/products/audit-logs", "/api/products/audit-logs/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.POST,"/api/products","/api/products/","/api/products/batch-delete","/api/products/batch-delete/","/api/products/batch-deactivate","/api/products/batch-deactivate/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.PATCH,"/api/products/*","/api/products/*/","/api/products/*/stock","/api/products/*/stock/").hasAuthority("PRODUCT_MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/", "/api/products/*", "/api/products/*/").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/products", "/api/products/", "/api/products/*", "/api/products/*/").permitAll()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN))
                        .accessDeniedHandler((request,response,error) -> {
                            response.setStatus(403); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
                            json.writeValue(response.getWriter(),vn.aims.auth.controller.AuthErrorHandler.body(403,"Bạn không có quyền truy cập chức năng này"));
                        }))
                .build();
    }
}
