package vn.aims.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;

/** Whitelisted DTO fields, then source business rules. No numeric string coercion. */
final class ProductAdminInput {
    static final List<String> DETAILS=List.of("book","cd","dvd","newspaper");
    static final Map<String,String> COMMON=spec("productType:s title:s category:s description:s barcode:s length:n width:n height:n weight:n originalPrice:n currentPrice:n quantityInStock:i0 status:s imageUrl:s");
    static final Map<String,Map<String,String>> SUBTYPES=Map.of(
            "book",spec("authors:s coverType:s publisher:s publicationDate:d numPages:i1 language:s genre:s"),
            "cd",spec("artists:s recordLabel:s genre:s releaseDate:d tracks:a"),
            "dvd",spec("discType:s director:s runtimeMinutes:i1 studio:s language:s subtitles:s releaseDate:d genre:s"),
            "newspaper",spec("editorInChief:s publisher:s publicationDate:d issueNumber:s frequency:s issn:s language:s sections:s"));
    static final Map<String,Set<String>> REQUIRED=Map.of(
            "",Set.of("productType","title","category","barcode","weight","originalPrice","currentPrice","quantityInStock"),
            "book",Set.of("authors","coverType","publisher","publicationDate"),"cd",Set.of("artists","recordLabel","genre"),
            "dvd",Set.of("discType","director","runtimeMinutes","studio","language","subtitles"),
            "newspaper",Set.of("editorInChief","publisher","publicationDate"));
    static final class Invalid extends RuntimeException {
        final List<String> messages;
        Invalid(List<String> errors) { messages=List.copyOf(errors); }
    }
    private static Map<String,String> spec(String value) {
        var map=new LinkedHashMap<String,String>();for(String field:value.split(" ")) { var parts=field.split(":");map.put(parts[0],parts[1]); } return map;
    }
    static ObjectNode parse(JsonNode body,boolean update) {
        var errors=new ArrayList<String>();
        ObjectNode result=fields(body,COMMON,update?Set.of():REQUIRED.get(""),"",errors);
        for(String key:DETAILS) if(body.has(key)) {
            var detail=body.get(key);
            if(detail.isNull()) { result.set(key,detail);continue; }
            if(!detail.isObject()) { errors.add("nested property "+key+" must be either object or array");continue; }
            var clean=fields(detail,SUBTYPES.get(key),update?Set.of():REQUIRED.get(key),key+".",errors);
            if(key.equals("cd") && detail.path("tracks").isArray()) {
                var tracks=clean.putArray("tracks"); int index=0;
                for(var track:detail.get("tracks")) tracks.add(fields(track,spec("title:s lengthSeconds:i1"),Set.of("title","lengthSeconds"),"cd.tracks."+(index++)+".",errors));
            }
            result.set(key,clean);
        }
        if(!errors.isEmpty()) throw new Invalid(errors);
        return result;
    }
    private static ObjectNode fields(JsonNode body,Map<String,String> fields,Set<String> required,String prefix,List<String> errors) {
        var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        for(var entry:fields.entrySet()) {
            String name=entry.getKey(),label=prefix+name,type=entry.getValue();var value=body.get(name);
            if(value!=null) result.set(name,value);
            if((value==null || value.isNull()) && !required.contains(name)) continue;
            boolean number=value!=null && value.isNumber() && Double.isFinite(value.doubleValue());
            if(type.equals("s") && (value==null || !value.isTextual())) errors.add(label+" must be a string");
            if(type.equals("n") && !number) errors.add(label+" must be a number conforming to the specified constraints");
            if(type.startsWith("i")) {
                int min=Integer.parseInt(type.substring(1));
                if(!number || value.doubleValue()<min) errors.add(label+" must not be less than "+min);
                if(!number || value.decimalValue().stripTrailingZeros().scale()>0) errors.add(label+" must be an integer number");
            }
            if(type.equals("a") && (value==null || !value.isArray())) errors.add(label+" must be an array");
            if(type.equals("d") && !date(value)) errors.add(label+" must be a valid ISO 8601 date string");
        }
        return result;
    }
    private static boolean date(JsonNode node) {
        if(node==null || !node.isTextual()) return false;
        try {
            String value=node.asText();
            if(value.length()==10) java.time.LocalDate.parse(value);
            else java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(value);
            return true;
        } catch(java.time.DateTimeException error) { return false; }
    }
    static String business(ObjectNode dto,Product existing) {
        boolean update=existing!=null;
        String type=update?existing.productType:dto.path("productType").asText();
        type=type.toUpperCase(Locale.ROOT); String key=type.toLowerCase(Locale.ROOT);
        if(!DETAILS.contains(key)) fail("Unsupported productType: "+type);
        if(dto.has("productType")) {
            if(dto.get("productType").isNull()) fail("productType cannot be changed");
            dto.put("productType",dto.get("productType").asText().toUpperCase(Locale.ROOT));
        }
        if(!update && (DETAILS.stream().filter(dto::has).count()!=1 || !dto.has(key))) fail("productType "+type+" requires exactly one "+key+" detail object");
        if(update) {
            for(String other:DETAILS) if(!other.equals(key) && dto.has(other)) fail("Cannot update "+other+" detail for "+key+" product");
            if(dto.has("productType") && !dto.get("productType").asText().equals(existing.productType)) fail("productType cannot be changed");
            if(dto.has("originalPrice") && decimal(dto.get("originalPrice")).compareTo(existing.originalPrice)!=0) fail("originalPrice cannot be changed");
        }
        if(!update) for(String field:List.of("title","category","barcode")) required(dto.get(field),field);
        for(String field:List.of("weight","length","width","height")) if(dto.hasNonNull(field)) positive(dto.get(field),field);
        if(dto.has("quantityInStock") && (dto.get("quantityInStock").isNull() || dto.get("quantityInStock").asDouble()<0)) fail("quantityInStock must be a non-negative integer");
        BigDecimal original=dto.hasNonNull("originalPrice")?decimal(dto.get("originalPrice")):existing.originalPrice;
        BigDecimal current=dto.hasNonNull("currentPrice")?decimal(dto.get("currentPrice")):existing.currentPrice;
        if(original.signum()<=0) fail("originalPrice must be greater than 0");
        if(current.compareTo(original.multiply(new BigDecimal("0.3")))<0 || current.compareTo(original.multiply(new BigDecimal("1.5")))>0) fail("currentPrice must be between 30% and 150% of originalPrice");
        var detail=dto.get(key);
        if(detail!=null && !detail.isNull()) {
            if(!update) for(String field:SUBTYPES.get(key).keySet().stream().filter(REQUIRED.get(key)::contains).toList()) {
                if(SUBTYPES.get(key).get(field).startsWith("i")) positive(detail.get(field),key+"."+field);
                else required(detail.get(field),key+"."+field);
            }
            if(key.equals("cd") && detail.has("tracks")) {
                if(!detail.get("tracks").isArray()) fail("cd.tracks must be an array");
                for(var track:detail.get("tracks")) { required(track.get("title"),"cd.tracks.title"); positive(track.get("lengthSeconds"),"cd.tracks.lengthSeconds"); }
            }
        } else if(!update) fail("productType "+type+" requires exactly one "+key+" detail object");
        return key;
    }
    static List<Integer> ids(JsonNode body) {
        var value=body.get("ids");var errors=new ArrayList<String>();
        if(value==null || !value.isArray()) throw new Invalid(List.of("each value in ids must be an integer number","ids must contain no more than 10 elements","ids must contain at least 1 elements","ids must be an array"));
        for(var id:value) if(!id.isNumber() || id.decimalValue().stripTrailingZeros().scale()>0) errors.add("each value in ids must be an integer number");
        if(value.size()>10) errors.add("ids must contain no more than 10 elements");
        if(value.isEmpty()) errors.add("ids must contain at least 1 elements");
        if(!errors.isEmpty()) throw new Invalid(errors);
        var ids=new ArrayList<Integer>();for(var id:value) { if(!id.canConvertToInt()) throw new CatalogException(500,"Internal server error");ids.add(id.intValue()); }
        if(new HashSet<>(ids).size()!=ids.size()) fail("ids must be unique");return ids;
    }
    static int delta(JsonNode body) {
        var errors=new ArrayList<String>();var delta=body.get("quantityDelta");var reason=body.get("reason");
        if(delta==null || !delta.isNumber() || delta.decimalValue().stripTrailingZeros().scale()>0) errors.add("quantityDelta must be an integer number");
        if(reason==null || !reason.isTextual() || reason.asText().isEmpty()) errors.add("reason must be longer than or equal to 1 characters");
        if(reason==null || !reason.isTextual()) errors.add("reason must be a string");
        if(!errors.isEmpty()) throw new Invalid(errors);
        if(!delta.canConvertToInt()) throw new CatalogException(500,"Internal server error"); return delta.intValue();
    }
    private static BigDecimal decimal(JsonNode value) { return value==null || value.isNull()?BigDecimal.ZERO:value.decimalValue(); }
    private static void positive(JsonNode value,String name) { if(value==null || !value.isNumber() || value.decimalValue().signum()<=0) fail(name+" must be greater than 0"); }
    private static void required(JsonNode value,String name) { if(value==null || !value.isTextual() || ProductSearch.trim(value.asText()).isEmpty()) fail(name+" is required"); }
    private static void fail(String message) { throw new CatalogException(400,message); }
}
