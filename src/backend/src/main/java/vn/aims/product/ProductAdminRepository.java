package vn.aims.product;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL identifiers come exclusively from internal maps; all submitted values are bound. */
@Repository
public class ProductAdminRepository {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final ObjectMapper json;
    private static final Map<String,String> COMMON=columns("productType:product_type title:title category:category description:description barcode:barcode length:length width:width height:height weight:weight originalPrice:original_value currentPrice:current_price quantityInStock:quantity_in_stock status:status imageUrl:image_url");
    private static final Map<String,String> BOOK=columns("authors:authors coverType:cover_type numPages:num_pages");
    private static final Map<String,String> DVD=columns("discType:disc_type director:director runtimeMinutes:runtime_minutes subtitles:subtitles");
    private static final Map<String,String> NEWS=columns("editorInChief:editor_in_chief issueNumber:issue_number frequency:frequency issn:issn sections:sections");
    public ProductAdminRepository(JdbcTemplate jdbc,EntityManager entities,ObjectMapper json) { this.jdbc=jdbc;this.entities=entities;this.json=json; }
    private static Map<String,String> columns(String mappings) {
        var result=new LinkedHashMap<String,String>();for(String mapping:mappings.split(" ")) { var parts=mapping.split(":");result.put(parts[0],parts[1]); } return result;
    }
    Product find(int id,boolean visible,boolean lock) {
        var rows=entities.createNativeQuery("SELECT * FROM products WHERE product_id=:id"+(visible?" AND status<>'DELETED'":"")+(lock?" FOR UPDATE":""),Product.class).setParameter("id",id).getResultList();
        return rows.isEmpty()?null:(Product)rows.getFirst();
    }
    int insert(JsonNode body) {
        var values=values(body,COMMON);String keys=String.join(",",values.keySet());
        return jdbc.queryForObject("INSERT INTO products ("+keys+") VALUES ("+marks(values.size())+") RETURNING product_id",Integer.class,values.values().toArray());
    }
    void update(int id,JsonNode body) {
        var fields=values(body,COMMON);fields.remove("product_type");
        if(fields.isEmpty()) return;
        var params=new ArrayList<>(fields.values());params.add(id);
        jdbc.update("UPDATE products SET "+assignments(fields)+",updated_at=now() WHERE product_id=?",params.toArray());
    }
    void detail(int id,String type,JsonNode root) {
        var body=root.get(type);if(body==null || body.isNull()) return;
        String publisher=type.equals("cd")?"recordLabel":type.equals("dvd")?"studio":"publisher";
        String date=(type.equals("book") || type.equals("newspaper"))?"publicationDate":"releaseDate";
        var media=values(body,columns(publisher+":publisher language:language genre:genre"));
        if(type.equals("cd")) media.remove("language");
        if(type.equals("newspaper")) media.remove("genre");
        if(body.hasNonNull(date) && !body.get(date).asText().isEmpty()) {
            String raw=body.get(date).asText();
            // Source TypeORM persists a SQL date; preserve local date portion for date-only inputs.
            LocalDate day;
            if(raw.length()==10) day=LocalDate.parse(raw);
            else { try { day=OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate(); }
                   catch(java.time.format.DateTimeParseException noOffset) { day=LocalDateTime.parse(raw).toLocalDate(); } }
            media.put("release_date",day);
        }
        saveDetail("media",id,media);
        switch(type) {
            case "book" -> saveDetail("books",id,values(body,BOOK));
            case "dvd" -> saveDetail("dvds",id,values(body,DVD));
            case "newspaper" -> saveDetail("newspapers",id,values(body,NEWS));
            case "cd" -> {
                saveDetail("cds",id,values(body,columns("artists:artists")));
                if(body.has("tracks")) {
                    jdbc.update("DELETE FROM cd_tracks WHERE product_id=?",id);
                    for(var track:body.get("tracks")) jdbc.update("INSERT INTO cd_tracks (product_id,title,length_seconds) VALUES (?,?,?)",id,track.get("title").asText(),track.get("lengthSeconds").decimalValue());
                }
            }
            default -> throw new IllegalArgumentException("Unsupported detail table");
        }
    }
    private void saveDetail(String table,int id,Map<String,Object> fields) {
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE product_id=?)",Boolean.class,id))) {
            if(fields.isEmpty()) return;var params=new ArrayList<>(fields.values());params.add(id);
            jdbc.update("UPDATE "+table+" SET "+assignments(fields)+" WHERE product_id=?",params.toArray());
        } else {
            var all=new LinkedHashMap<String,Object>();all.put("product_id",id);all.putAll(fields);
            jdbc.update("INSERT INTO "+table+" ("+String.join(",",all.keySet())+") VALUES ("+marks(all.size())+")",all.values().toArray());
        }
    }
    private Map<String,Object> values(JsonNode body,Map<String,String> fields) {
        var result=new LinkedHashMap<String,Object>();fields.forEach((name,column)-> {
            if(body.has(name)) { var value=body.get(name); result.put(column,value.isNull()?null:value.isNumber()?value.decimalValue():value.asText()); }
        });return result;
    }
    private String marks(int count) { return String.join(",",Collections.nCopies(count,"?")); }
    private String assignments(Map<String,Object> fields) { return String.join(",",fields.keySet().stream().map(key->key+"=?").toList()); }
    void audit(int id,String action,Object changes,String actor,String reason) {
        // Timestamp after acquiring locks, not transaction start (which may precede midnight).
        try { jdbc.update("INSERT INTO product_logs(product_id,action_type,changed_fields,performed_by,reason,created_at) VALUES (?,?,?::jsonb,?,?,clock_timestamp())",id,action,json.writeValueAsString(changes),actor,reason); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Cannot serialize audit",error); }
    }
    void stock(int id,int quantity) { jdbc.update("UPDATE products SET quantity_in_stock=?,updated_at=now() WHERE product_id=?",quantity,id); }
    void status(int id,String status) { jdbc.update("UPDATE products SET status=?,updated_at=now() WHERE product_id=?",status,id); }
    void lockManager(String actor) { jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 5005))",actor); }
    long deleteCount(String actor,Instant start,Instant end) {
        return jdbc.queryForObject("SELECT count(*) FROM product_logs WHERE performed_by=? AND action_type IN ('DELETE','DEACTIVATE') AND created_at>=? AND created_at<=?",Long.class,
                actor,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end.minusMillis(1)));
    }
    boolean ordered(int id) {
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.order_items') IS NULL",Boolean.class))) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public.order_items WHERE product_id=?)",Boolean.class,id));
    }
    void clear() { entities.clear(); }
    List<Map<String,Object>> logs() {
        var logs=entities.createQuery("SELECT l FROM ProductLog l LEFT JOIN FETCH l.product ORDER BY l.createdAt DESC",ProductLog.class).getResultList();
        var date=new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
        return logs.stream().map(log->{
            Map<String,Object> result=new LinkedHashMap<>();result.put("logID",log.logID);result.put("actionType",log.actionType);
            result.put("changedFields",log.changedFields);result.put("performedBy",log.performedBy);result.put("reason",log.reason);
            result.put("createdAt",date.format(log.createdAt));result.put("product",log.product==null?null:ProductResponse.from(log.product));return result;
        }).toList();
    }
}
