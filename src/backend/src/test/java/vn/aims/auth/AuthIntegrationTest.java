package vn.aims.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import vn.aims.auth.service.AuthService;
import vn.aims.auth.security.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc @Transactional
@org.springframework.context.annotation.Import(AuthIntegrationTest.ProbeConfiguration.class)
class AuthIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class ProbeConfiguration {
        @org.springframework.context.annotation.Bean RoleProbe roleProbe() { return new RoleProbe(); }
    }
    static class RoleProbe {
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
        public boolean adminOrStaff() { return true; }
        @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PRODUCT_MANAGER')")
        public boolean manager() { return true; }
    }
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper json;
    @Autowired JwtTokens tokens;
    @Autowired RoleProbe probe;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired AuthService authService;
    @Autowired jakarta.persistence.EntityManager em;
    int id;
    @BeforeEach void seed() {
        id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES (?,?,?) RETURNING user_id",
                Integer.class,"auth@example.test",passwords.encode("old-password"),"Người dùng");
        jdbc.update("INSERT INTO users_roles SELECT ?,role_id FROM roles WHERE name IN ('ADMIN','STAFF')",id);
    }
    String login() throws Exception {
        var body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"auth@example.test\",\"password\":\"old-password\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.userID").value(id))
                .andExpect(jsonPath("$.user.fullName").value("Người dùng"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }
    @Test void loginClaimsAndBcryptAreCompatible() throws Exception {
        var jwt = tokens.verify(login());
        assertThat(((Number)jwt.getClaim("userID")).intValue()).isEqualTo(id);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("ADMIN","STAFF");
        assertThat(jwt.getExpiresAt().toEpochMilli()-jwt.getIssuedAt().toEpochMilli()).isEqualTo(86400000);
        assertThat(passwords.matches("old-password",jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,id))).isTrue();
    }
    @Test void springAuthenticationManagerAuthenticatesCredentialsAndAccountStatus() {
        var authenticated = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("auth@example.test", "old-password"));
        assertThat(authenticated.isAuthenticated()).isTrue();
        assertThat(authenticated.getName()).isEqualTo("auth@example.test");
        assertThat(authenticated.getCredentials()).isNull();
        assertThat(((AimsUserPrincipal) authenticated.getPrincipal()).getPassword()).isNull();
        assertThat(authenticated.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ADMIN", "STAFF");

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("auth@example.test", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        jdbc.update("UPDATE users SET status='DEACTIVATED' WHERE user_id=?",id);
        em.clear();
        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("auth@example.test", "old-password")))
                .isInstanceOf(DisabledException.class);
    }
    @Test void protectedRequestsUseSpringResourceServerInsteadOfCustomJwtFilter() {
        var filters = springSecurityFilterChain.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .toList();
        assertThat(filters).anyMatch(BearerTokenAuthenticationFilter.class::isInstance);
        assertThat(filters).noneMatch(filter -> filter.getClass().getName()
                .equals("vn.aims.auth.security.JwtAuthenticationFilter"));
    }
    @Test void loginFailureAndDeactivatedAccount() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"auth@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Tài khoản hoặc mật khẩu không chính xác"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"missing@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Tài khoản hoặc mật khẩu không chính xác"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Tài khoản hoặc mật khẩu không chính xác"));
        jdbc.update("UPDATE users SET status='DEACTIVATED' WHERE user_id=?",id);
        em.clear();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"auth@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Tài khoản đã bị vô hiệu hóa hoặc khóa"));
    }
    @Test void changePasswordWritesHashAndAuditTogether() throws Exception {
        mvc.perform(post("/api/auth/change-password").header("Authorization","Bearer "+login()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
        em.flush();
        assertThat(passwords.matches("new-password",jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,id))).isTrue();
        assertThat(jdbc.queryForObject("SELECT action FROM user_audit_logs WHERE user_id=?",String.class,id)).isEqualTo("CHANGE_PASSWORD");
    }
    @Test void rejectsWrongOldAndShortNewPasswordsWithoutAudit() throws Exception {
        String token = login();
        for (String body : new String[]{"{\"oldPassword\":\"wrong\",\"newPassword\":\"new-password\"}","{\"oldPassword\":\"old-password\",\"newPassword\":\" 123 \"}"})
            mvc.perform(post("/api/auth/change-password").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_logs",Integer.class)).isZero();
    }
    @Test void guardDistinguishesMissingMalformedAndExpiredTokens() throws Exception {
        mvc.perform(post("/api/auth/change-password")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AccessTokenErrors.MISSING));
        mvc.perform(post("/api/auth/change-password").header("Authorization","bearer bad")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AccessTokenErrors.MISSING));
        mvc.perform(post("/api/auth/change-password").header("Authorization","Bearer bad")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AccessTokenErrors.INVALID));
    }
    @Test void corsPreflightAndStaleTokenOnPublicCatalog() throws Exception {
        mvc.perform(options("/api/auth/change-password").header("Origin","http://localhost:4200")
                .header("Access-Control-Request-Headers","authorization,content-type"))
                .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Headers","authorization,content-type"));
        mvc.perform(get("/api/products").header("Authorization","Bearer stale")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header("Origin","https://untrusted.example").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test @org.springframework.security.test.context.support.WithMockUser(authorities="ADMIN")
    void adminDoesNotImplicitlyGrantManager() {
        assertThat(probe.adminOrStaff()).isTrue();
        assertThatThrownBy(probe::manager).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test @org.springframework.security.test.context.support.WithMockUser(authorities="STAFF")
    void anyDeclaredRoleIsEnough() { assertThat(probe.adminOrStaff()).isTrue(); }
    @Test @org.springframework.security.test.context.support.WithMockUser(authorities="admin")
    void rolesAreCaseSensitive() {
        assertThatThrownBy(probe::adminOrStaff).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void auditFailureRollsBackPasswordChange() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String original=passwords.encode("old-password");
        int target=tx.execute(status -> jdbc.queryForObject(
                "INSERT INTO users(email,password_hash,full_name) VALUES (?,?,?) RETURNING user_id",
                Integer.class,"long".repeat(14)+"@example.test",original,"Rollback"));
        try {
            // Original performed_by is varchar(50); failure must roll back the credential update.
            assertThatThrownBy(() -> tx.execute(status -> authService.changePassword(target,"old-password","new-password")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            String retained = tx.execute(status -> jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,target));
            assertThat(retained).isEqualTo(original);
        } finally { tx.executeWithoutResult(status -> jdbc.update("DELETE FROM users WHERE user_id=?",target)); }
    }
}
