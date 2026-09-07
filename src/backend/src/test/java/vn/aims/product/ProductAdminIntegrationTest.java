package vn.aims.product;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.aims.auth.JwtTokens;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
class ProductAdminIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokens tokens;
    @Autowired ObjectMapper json;
    @Autowired ProductAdminService admin;
    String manager;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM product_logs");jdbc.update("DELETE FROM products");
        manager="Bearer "+tokens.issue(7,"manager@example.test","Manager",List.of("PRODUCT_MANAGER"));
    }
    ObjectNode payload(String type,int stock) throws Exception {
        var body=(ObjectNode)json.readTree("""
                {"title":"New product","category":"Media","barcode":"unique","weight":1,
                 "originalPrice":100,"currentPrice":100,"quantityInStock":0}
                """);
        body.put("productType",type).put("quantityInStock",stock).put("barcode",UUID.randomUUID().toString());
        String detail=switch(type) {
            case "BOOK" -> "{\"authors\":\"Author\",\"coverType\":\"Hard\",\"publisher\":\"Pub\",\"publicationDate\":\"2025-02-01\"}";
            case "CD" -> "{\"artists\":\"Artist\",\"recordLabel\":\"Label\",\"genre\":\"Jazz\",\"tracks\":[{\"title\":\"Track\",\"lengthSeconds\":120}]}";
            case "DVD" -> "{\"discType\":\"DVD\",\"director\":\"Director\",\"runtimeMinutes\":90,\"studio\":\"Studio\",\"language\":\"vi\",\"subtitles\":\"English\"}";
            default -> "{\"editorInChief\":\"Editor\",\"publisher\":\"Pub\",\"publicationDate\":\"2025-02-01\"}";
        };
        body.set(type.toLowerCase(Locale.ROOT),json.readTree(detail)); return body;
    }
    JsonNode create(String type,int stock) throws Exception { return create(payload(type,stock)); }
    JsonNode create(ObjectNode body) throws Exception {
        return json.readTree(mvc.perform(post("/api/products").header("Authorization",manager).header("x-manager-id","forged-header")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void createAllSubtypesPreservesPublicContractAndSignedAttribution() throws Exception {
        for(String type:List.of("BOOK","CD","DVD","NEWSPAPER")) {
            var body=create(type,2); int id=body.get("productID").asInt();
            assertThat(body.get("currentPrice").asText()).isEqualTo("100.00");
            assertThat(body.get(type.toLowerCase(Locale.ROOT)).has("recordLabel")).isTrue();
            mvc.perform(get("/api/products/"+id)).andExpect(content().json(body.toString(),true));
        }
        assertThat(jdbc.queryForList("SELECT DISTINCT performed_by FROM product_logs",String.class)).containsExactly("manager@example.test");
    }
    @Test void guardsAndManagerHeaderAreRequired() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(payload("BOOK",1).toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/audit-logs").header("Authorization","Bearer "+tokens.issue(1,"a@example.test","A",List.of("ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/audit-logs").header("Authorization",manager)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("x-manager-id header is required"));
        mvc.perform(get("/api/products").header("Authorization","Bearer stale")).andExpect(status().isOk());
    }
    @Test void partialCdUpdatePreservesMediaAndReplacesOnlySuppliedTracks() throws Exception {
        int id=create("CD",0).get("productID").asInt();
        mvc.perform(patch("/api/products/"+id).header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cd\":{\"artists\":\"Changed\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cd.tracks.length()").value(1)).andExpect(jsonPath("$.cd.recordLabel").value("Label"));
        mvc.perform(patch("/api/products/"+id).header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cd\":{\"tracks\":[]},\"unrecognized\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cd.tracks.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT changed_fields->'after' ? 'unrecognized' FROM product_logs WHERE action_type='UPDATE' ORDER BY log_id DESC LIMIT 1",Boolean.class)).isFalse();
    }
    @Test void priceBoundsTypeAndOriginalPriceAreEnforced() throws Exception {
        int id=create("BOOK",0).get("productID").asInt();
        for(String body:List.of("{\"originalPrice\":101}","{\"productType\":\"DVD\"}","{\"currentPrice\":29}","{\"currentPrice\":151}","{\"cd\":{}}"))
            mvc.perform(patch("/api/products/"+id).header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        for(int price:List.of(30,150))
            mvc.perform(patch("/api/products/"+id).header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON).content("{\"currentPrice\":"+price+"}"))
                    .andExpect(status().isOk());
    }
    @Test void stockIsAtomicAndRejectsNegative() throws Exception {
        int id=create("BOOK",1).get("productID").asInt();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<Integer>>();
            for(int i=0;i<2;i++) calls.add(pool.submit(()->{start.await();return mvc.perform(patch("/api/products/"+id+"/stock")
                    .header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantityDelta\":-1,\"reason\":\"sold\"}")).andReturn().getResponse().getStatus();}));
            start.countDown(); assertThat(List.of(calls.get(0).get(15,TimeUnit.SECONDS),calls.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,400);
        }
        assertThat(jdbc.queryForObject("SELECT quantity_in_stock FROM products WHERE product_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_logs WHERE action_type='STOCK_ADJUST'",Integer.class)).isEqualTo(1);
    }
    @Test void batchStatusOrderMissingAndDuplicateRules() throws Exception {
        int zero=create("BOOK",0).get("productID").asInt(),stock=create("DVD",2).get("productID").asInt();
        mvc.perform(post("/api/products/batch-delete").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":["+zero+",-1,"+stock+"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.results[0].status").value("DELETED"))
                .andExpect(jsonPath("$.results[1].status").value("NOT_FOUND")).andExpect(jsonPath("$.results[2].status").value("DEACTIVATED"));
        mvc.perform(post("/api/products/batch-delete").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1,1]}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("ids must be unique"));
        mvc.perform(get("/api/products/"+zero)).andExpect(status().isNotFound());
    }
    @Test void orderedProductIsDeactivatedAndSchemaIsNotCreatedByRuntime() throws Exception {
        int id=create("BOOK",0).get("productID").asInt();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.order_items') IS NULL",Boolean.class)).isTrue();
        // Fixture only: emulate Module7's product reference; never a migration or runtime stub.
        jdbc.execute("CREATE TABLE order_items (product_id integer)");
        try {
            jdbc.update("INSERT INTO order_items VALUES (?)",id);
            mvc.perform(post("/api/products/batch-delete").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":["+id+"]}" )).andExpect(status().isCreated()).andExpect(jsonPath("$.results[0].status").value("DEACTIVATED_ORDERED"));
        } finally { jdbc.execute("DROP TABLE order_items"); }
    }
    @Test void quotaCannotBeBypassedByHeaderOrConcurrentRequests() throws Exception {
        int id=create("BOOK",2).get("productID").asInt();
        jdbc.update("INSERT INTO product_logs(action_type,performed_by) SELECT 'DELETE','manager@example.test' FROM generate_series(1,19)");
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<Integer>>();
            for(String header:List.of("spoof-one","spoof-two")) calls.add(pool.submit(()->{start.await(); return mvc.perform(post("/api/products/batch-deactivate")
                    .header("Authorization",manager).header("x-manager-id",header).contentType(MediaType.APPLICATION_JSON).content("{\"ids\":["+id+"]}"))
                    .andReturn().getResponse().getStatus();}));
            start.countDown();assertThat(List.of(calls.get(0).get(15,TimeUnit.SECONDS),calls.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,400);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_logs WHERE action_type IN ('DELETE','DEACTIVATE')",Integer.class)).isEqualTo(20);
    }
    @Test void failedSubtypeOrAuditRollsBackAllProductWrites() throws Exception {
        var body=payload("BOOK",0);((ObjectNode)body.get("book")).put("coverType","x".repeat(51));
        mvc.perform(post("/api/products").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products",Integer.class)).isZero();
        String longActor="Bearer "+tokens.issue(1,"x".repeat(101)+"@example.test","Long",List.of("PRODUCT_MANAGER"));
        mvc.perform(post("/api/products").header("Authorization",longActor).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON).content(payload("BOOK",0).toString()))
                .andExpect(status().isInternalServerError());
        for(String table:List.of("products","media","books","product_logs")) assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void auditJsonAndNullProductRelationArePreserved() throws Exception {
        int id=create("BOOK",0).get("productID").asInt();
        mvc.perform(patch("/api/products/"+id+"/stock").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantityDelta\":3,\"reason\":\"New delivery\"}" )).andExpect(status().isOk());
        mvc.perform(get("/api/products/audit-logs").header("Authorization",manager).header("x-manager-id","x"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].actionType").value("STOCK_ADJUST"))
                .andExpect(jsonPath("$[0].changedFields.from").value(0)).andExpect(jsonPath("$[0].changedFields.to").value(3))
                .andExpect(jsonPath("$[0].reason").value("New delivery")).andExpect(jsonPath("$[0].product.currentPrice").value("100.00"));
        jdbc.update("DELETE FROM products WHERE product_id=?",id);
        mvc.perform(get("/api/products/audit-logs").header("Authorization",manager).header("x-manager-id","x"))
                .andExpect(jsonPath("$[0].product").isEmpty());
    }
    @Test void validationWhitelistAndBatchBounds() throws Exception {
        mvc.perform(post("/api/products").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isArray());
        for(String ids:List.of("[]","[\"1\"]","[1,2,3,4,5,6,7,8,9,10,11]"))
            mvc.perform(post("/api/products/batch-delete").header("Authorization",manager).header("x-manager-id","x").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":"+ids+"}"))
                    .andExpect(status().isBadRequest());
        var body=payload("BOOK",0);body.put("unknown",true);((ObjectNode)body.get("book")).put("unexpected",42);
        create(body);
        assertThat(jdbc.queryForObject("SELECT changed_fields->'after' ? 'unknown' OR changed_fields->'after'->'book' ? 'unexpected' FROM product_logs",Boolean.class)).isFalse();
    }
    @Test void quotaCountsOnlyCurrentDayExistingProductsAndSignedManager() throws Exception {
        int id=create("DVD",1).get("productID").asInt();
        jdbc.update("INSERT INTO product_logs(action_type,performed_by,created_at) SELECT 'DELETE','manager@example.test',now()-interval '2 day' FROM generate_series(1,20)");
        jdbc.update("INSERT INTO product_logs(action_type,performed_by) SELECT 'DELETE','other@example.test' FROM generate_series(1,20)");
        mvc.perform(post("/api/products/batch-deactivate").header("Authorization",manager).header("x-manager-id","changed").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[-1,"+id+"]}")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_logs WHERE action_type='DEACTIVATE'",Integer.class)).isEqualTo(1);
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void auditSchemaMatchesOriginalTypeorm() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA legacy_product_audit");statement.execute("SET LOCAL search_path=legacy_product_audit,public");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("product-admin/typeorm-schema.sql"));
                statement.execute("SET LOCAL search_path=public");
            }return null;
        });
        String columns="""
                SELECT column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale,datetime_precision,
                  replace(replace(column_default,'legacy_product_audit.',''),'public.','') AS default_value
                FROM information_schema.columns WHERE table_schema=? AND table_name='product_logs' ORDER BY column_name
                """;
        assertThat(jdbc.queryForList(columns,"public")).isEqualTo(jdbc.queryForList(columns,"legacy_product_audit"));
        String constraints="""
                SELECT c.conname,c.contype,replace(replace(pg_get_constraintdef(c.oid),'legacy_product_audit.',''),'public.','') AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='product_logs' ORDER BY c.conname
                """;
        assertThat(jdbc.queryForList(constraints,"public")).isEqualTo(jdbc.queryForList(constraints,"legacy_product_audit"));
        String indexes="SELECT indexname,replace(replace(indexdef,'legacy_product_audit.',''),'public.','') AS definition FROM pg_indexes WHERE schemaname=? AND tablename='product_logs' ORDER BY indexname";
        assertThat(jdbc.queryForList(indexes,"public")).isEqualTo(jdbc.queryForList(indexes,"legacy_product_audit"));
    }
}
