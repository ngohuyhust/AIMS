package vn.aims.product;

import java.nio.charset.StandardCharsets;
import java.sql.Savepoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Sql("/catalog/seed.sql")
class CatalogIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired ProductRepository repository;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 7})
    void detailMatchesOriginalTypeormJsonIncludingNullsAndAliases(int id) throws Exception {
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk())
                .andExpect(content().json(fixture("detail-" + id + ".json"), true))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void searchMatchesOriginalJsonAndDoesNotLoadSubtypes() throws Exception {
        mvc.perform(get("/api/products").header("Authorization", "Bearer stale-token"))
                .andExpect(status().isOk()).andExpect(content().json(fixture("search.json"), true));
    }

    @Test
    void repositorySearchesSubtypeTextWithoutDuplicatingTrackMatches() {
        for (String keyword : List.of("author needle", "artist needle", "track needle", "director needle", "editor needle", "science needle", "Publisher One", "Vietnamese", "Jazz")) {
            assertThat(repository.search(ProductSearch.parse(keyword,null,null,null,null,null))).hasSize(1);
        }
        assertThat(repository.search(ProductSearch.parse("' OR 1=1 --",null,null,null,null,null))).isEmpty();
        assertThat(repository.search(ProductSearch.parse("%",null,null,null,null,null))).hasSize(6);
    }

    @Test
    void categoryAliasesPriceBoundariesAndStatusMatchLegacy() throws Exception {
        mvc.perform(get("/api/products").param("category","SÁCH").param("minPrice","30000").param("maxPrice","30000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].productID").value(1));
        mvc.perform(get("/api/products").param("mediaTypes"," cd, CD, dvd,,"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/products").param("status","all"))
                .andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/products").param("status","deleted"))
                .andExpect(jsonPath("$[0].productID").value(6));
        mvc.perform(get("/api/products").param("minPrice","2").param("maxPrice","1"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void randomIsLimitedToTwentyDistinctActiveBaseProducts() throws Exception {
        jdbc.update("""
                INSERT INTO products(product_id,product_type,title,category,barcode,weight,original_value,current_price)
                SELECT n,'BOOK','Random '||n,'BOOK','R-'||n,1,100,100 FROM generate_series(100,124) n
                """);
        var random = repository.random();
        assertThat(random).hasSize(20).extracting(p -> p.productID).doesNotHaveDuplicates();
        assertThat(random).allMatch(p -> p.status.equals("ACTIVE"));
        mvc.perform(get("/api/products/random").param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].book").doesNotExist());
    }

    @Test
    void invalidInputAndMissingProductsUseNestErrorEnvelopes() throws Exception {
        mvc.perform(get("/api/products").param("minPrice","NaN"))
                .andExpect(status().isBadRequest()).andExpect(content().json("""
                {"message":"minPrice must be a number","error":"Bad Request","statusCode":400}
                """,true));
        mvc.perform(get("/api/products").param("mediaTypes","book,tape"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid media type: TAPE"));
        mvc.perform(get("/api/products/1.2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation failed (numeric string is expected)"));
        for (int id : new int[]{6,999,-1,0}) {
            mvc.perform(get("/api/products/"+id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product with ID " + id + " not found"));
        }
        mvc.perform(get("/api/products/8")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported productType: UNKNOWN"));
    }

    @Test
    void adminAndMutationRoutesRemainClosed() throws Exception {
        mvc.perform(get("/api/products/audit-logs")).andExpect(status().isForbidden());
        mvc.perform(post("/api/products").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/products/1").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogCorsReflectsAllowedOriginsWithoutRejectingOtherNormalRequests() throws Exception {
        mvc.perform(get("/api/products").header("Origin","http://localhost:4200"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"));
        mvc.perform(options("/api/products").header("Origin","https://preview.vercel.app")
                        .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","authorization,x-manager-id"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Headers","authorization,x-manager-id"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(get("/api/products").header("Origin","https://untrusted.example"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void schemaPreservesConstraintsCascadesAndNullableTrackForeignKey() {
        for (String sql : List.of("UPDATE products SET quantity_in_stock=-1 WHERE product_id=1",
                "UPDATE products SET current_price=29999.99 WHERE product_id=1",
                "UPDATE products SET current_price=150000.01 WHERE product_id=1",
                "UPDATE products SET barcode='BOOK-1' WHERE product_id=2",
                "INSERT INTO media(product_id) VALUES(999)")) {
            jdbc.execute((ConnectionCallback<Void>) c -> {
                Savepoint savepoint=c.setSavepoint();
                try (var statement=c.createStatement()) {
                    assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(java.sql.SQLException.class);
                } finally { c.rollback(savepoint); }
                return null;
            });
        }
        jdbc.update("INSERT INTO cd_tracks(track_id,title,length_seconds) VALUES(99,'Orphan permitted by source',1)");
        jdbc.update("DELETE FROM products WHERE product_id=2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cd_tracks WHERE product_id=2",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media WHERE product_id=2",Integer.class)).isZero();
    }

    private String fixture(String name) throws Exception {
        return new ClassPathResource("catalog/"+name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void flywaySchemaMatchesOriginalTypeormColumnsConstraintsAndIndexes() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA legacy_catalog");
                statement.execute("SET LOCAL search_path=legacy_catalog");
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("catalog/typeorm-schema.sql"));
                statement.execute("SET LOCAL search_path=public");
            }
            return null;
        });
        String columns="""
                SELECT table_name,column_name,data_type,is_nullable,character_maximum_length,
                       numeric_precision,numeric_scale,datetime_precision,
                       replace(replace(column_default,'legacy_catalog.',''),'public.','') AS default_value
                FROM information_schema.columns WHERE table_schema=? AND table_name IN ('products','media','books','cds','cd_tracks','dvds','newspapers')
                ORDER BY table_name,column_name
                """;
        assertThat(jdbc.queryForList(columns,"public")).isEqualTo(jdbc.queryForList(columns,"legacy_catalog"));
        String constraints="""
                SELECT t.relname,c.conname,c.contype,
                       replace(replace(pg_get_constraintdef(c.oid),'legacy_catalog.',''),'public.','') AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname IN ('products','media','books','cds','cd_tracks','dvds','newspapers') ORDER BY t.relname,c.conname
                """;
        assertThat(jdbc.queryForList(constraints,"public")).isEqualTo(jdbc.queryForList(constraints,"legacy_catalog"));
        String indexes="""
                SELECT tablename,indexname,replace(replace(indexdef,'legacy_catalog.',''),'public.','') AS definition
                FROM pg_indexes WHERE schemaname=? AND tablename IN ('products','media','books','cds','cd_tracks','dvds','newspapers') ORDER BY tablename,indexname
                """;
        assertThat(jdbc.queryForList(indexes,"public")).isEqualTo(jdbc.queryForList(indexes,"legacy_catalog"));
    }
}
