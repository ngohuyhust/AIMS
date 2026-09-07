package vn.aims.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.persistence.EntityManager;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class UserDomainIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserAuditLogRepository logs;
    @Autowired UserDomainService service;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired Flyway flyway;

    User account() {
        return users.saveAndFlush(new User("domain@example.test", "synthetic-test-hash", "Domain Test", null,
                roles.findByNameIn(List.of("ADMIN", "PRODUCT_MANAGER"))));
    }

    @Test void roleSeedsAreSafeAndMigrationIsRepeatable() {
        assertThat(roles.findAll()).extracting(Role::getName).containsExactlyInAnyOrder("ADMIN", "PRODUCT_MANAGER", "STAFF");
        assertThat(users.count()).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(users.count()).isZero();
        assertThat(roles.count()).isEqualTo(3);
    }

    @Test void roundTripPreservesRolesNullsStatusTimestampsAndHidesHash() throws Exception {
        var user = account();
        var created = user.getCreatedAt();
        em.clear();
        var loaded = service.findById(user.getUserID()).orElseThrow();
        assertThat(loaded.getRoles()).extracting(Role::getName).containsExactlyInAnyOrder("ADMIN", "PRODUCT_MANAGER");
        assertThat(loaded.getPhoneNumber()).isNull();
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
        assertThat(loaded.getCreatedAt()).isEqualTo(created);
        assertThat(loaded.getUpdatedAt()).isEqualTo(created);
        assertThat(loaded.getPasswordHash()).isEqualTo("synthetic-test-hash");
        var payload = json.valueToTree(loaded);
        assertThat(payload.has("passwordHash")).isFalse();
        assertThat(payload.get("userID").asInt()).isEqualTo(user.getUserID());
        assertThat(service.findByEmail("domain@example.test")).isPresent();
        assertThat(service.findByEmail("DOMAIN@example.test")).isEmpty();
        assertThat(service.findById(-1)).isEmpty();
    }

    @Test void changingAccountDoesNotResetCredentialsOrRolesAndUpdatesTimestamp() {
        var user = account();
        var created = user.getCreatedAt();
        user.status = "DEACTIVATED";
        user.fullName = "Changed";
        users.flush();
        em.clear();
        var loaded = service.findById(user.getUserID()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo("DEACTIVATED");
        assertThat(loaded.getPasswordHash()).isEqualTo("synthetic-test-hash");
        assertThat(loaded.getRoles()).hasSize(2);
        assertThat(loaded.getCreatedAt()).isEqualTo(created);
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(created);
    }

    @Test void deletingUserKeepsAuditAndSharedRolesButCascadesMemberships() {
        var user = account();
        var log = logs.saveAndFlush(new UserAuditLog("DOMAIN_TEST", null, null, user));
        jdbc.update("delete from users where user_id=?", user.getUserID());
        em.clear();
        assertThat(jdbc.queryForObject("select count(*) from users_roles", Integer.class)).isZero();
        assertThat(roles.count()).isEqualTo(3);
        var retained = logs.findById(log.getLogID()).orElseThrow();
        assertThat(retained.getUser()).isNull();
        assertThat(retained.getCreatedAt()).isNotNull();
        assertThat(retained.getDescription()).isNull();
    }

    @Test void duplicateEmailIsRejected() {
        account();
        assertThatThrownBy(() -> users.saveAndFlush(new User("domain@example.test", "synthetic-test-hash", "Duplicate", null, List.of())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void duplicateRoleIsRejected() {
        assertThatThrownBy(() -> jdbc.update("insert into roles(name) values ('ADMIN')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void referencedRoleCannotBeDeleted() {
        account();
        assertThatThrownBy(() -> jdbc.update("delete from roles where name='ADMIN'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void duplicateMembershipIsRejected() {
        account();
        assertThatThrownBy(() -> jdbc.update("insert into users_roles select * from users_roles limit 1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void schemaMatchesOriginalTypeormColumnsConstraintsAndIndexes() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA legacy_users");
                statement.execute("SET LOCAL search_path=legacy_users");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.ClassPathResource("user/typeorm-schema.sql"));
                statement.execute("SET LOCAL search_path=public");
            }
            return null;
        });
        String columns = """
                SELECT table_name,column_name,data_type,is_nullable,character_maximum_length,
                       numeric_precision,numeric_scale,datetime_precision,
                       replace(replace(column_default,'legacy_users.',''),'public.','') AS default_value
                FROM information_schema.columns WHERE table_schema=?
                  AND table_name IN ('users','roles','users_roles','user_audit_logs')
                ORDER BY table_name,column_name
                """;
        assertThat(jdbc.queryForList(columns, "public")).isEqualTo(jdbc.queryForList(columns, "legacy_users"));
        String constraints = """
                SELECT t.relname,c.conname,c.contype,
                       replace(replace(pg_get_constraintdef(c.oid),'legacy_users.',''),'public.','') AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname IN ('users','roles','users_roles','user_audit_logs')
                ORDER BY t.relname,c.conname
                """;
        assertThat(jdbc.queryForList(constraints, "public")).isEqualTo(jdbc.queryForList(constraints, "legacy_users"));
        String indexes = """
                SELECT tablename,indexname,replace(replace(indexdef,'legacy_users.',''),'public.','') AS definition
                FROM pg_indexes WHERE schemaname=? AND tablename IN ('users','roles','users_roles','user_audit_logs')
                ORDER BY tablename,indexname
                """;
        assertThat(jdbc.queryForList(indexes, "public")).isEqualTo(jdbc.queryForList(indexes, "legacy_users"));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void upgradeFromV2PreservesCatalogAndRepeatedStartupPreservesAccounts() throws Exception {
        String schema = "module2_upgrade";
        var oldVersion = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("2").load();
        var newVersion = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("3").load();
        try {
            oldVersion.migrate();
            try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("SET search_path=module2_upgrade");
                statement.execute("""
                        INSERT INTO products(product_type,title,category,barcode,weight,original_value,current_price)
                        VALUES ('BOOK','Preserved catalog row','Book','module2-upgrade',1,100,100)
                        """);
                assertThat(newVersion.migrate().migrationsExecuted).isEqualTo(1);
                statement.execute("""
                        INSERT INTO users(email,password_hash,full_name,status)
                        VALUES ('upgrade@example.test','synthetic-test-hash','Upgrade','DEACTIVATED')
                        """);
                statement.execute("INSERT INTO users_roles SELECT user_id,role_id FROM users CROSS JOIN roles WHERE name='STAFF'");
                assertThat(newVersion.migrate().migrationsExecuted).isZero();
                assertThat(newVersion.validateWithResult().validationSuccessful).isTrue();
                try (var rows = statement.executeQuery("SELECT title FROM products")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("Preserved catalog row");
                }
                try (var rows = statement.executeQuery("""
                        SELECT email,password_hash,status,name FROM users
                        JOIN users_roles USING(user_id) JOIN roles USING(role_id)
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("upgrade@example.test");
                    assertThat(rows.getString(2)).isEqualTo("synthetic-test-hash");
                    assertThat(rows.getString(3)).isEqualTo("DEACTIVATED");
                    assertThat(rows.getString(4)).isEqualTo("STAFF");
                    assertThat(rows.next()).isFalse();
                }
            }
        } finally {
            // Only the schema owned by this test in its disposable Testcontainer.
            jdbc.execute("DROP SCHEMA IF EXISTS module2_upgrade CASCADE");
        }
    }
}
