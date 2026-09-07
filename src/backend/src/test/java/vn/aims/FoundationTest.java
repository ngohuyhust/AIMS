package vn.aims;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate http;
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;

    @Test
    void healthIsPublicAndDoesNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void embeddedServerServesHealthOverHttp() {
        var response = http.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void unimplementedEndpointsAndActuatorInternalsAreNotExposed() throws Exception {
        for (String path : new String[]{"/api/orders", "/api/payments", "/actuator/env", "/actuator"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    void flywayRunsOnPostgresqlAndCanBeRepeatedWithOnlyAuthorizedTables() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17.");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' order by table_name
                """, String.class)).containsExactly("books", "cd_tracks", "cds", "delivery_info", "dvds", "flyway_schema_history", "invoices", "media", "newspapers", "order_items", "orders", "product_logs", "products", "roles", "user_audit_logs", "users", "users_roles");
    }

    @Test
    void schemaIsValidatedAndDestructiveFlywayOperationsAreDisabled() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isFalse();
    }
}
