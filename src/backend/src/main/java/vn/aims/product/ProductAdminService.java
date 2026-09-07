package vn.aims.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class ProductAdminService {
    private final ProductAdminRepository repository;
    public ProductAdminService(ProductAdminRepository repository) { this.repository=repository; }
    public int create(ObjectNode dto,String actor) {
        String key=ProductAdminInput.business(dto,null);int id=repository.insert(dto);
        repository.detail(id,key,dto);repository.audit(id,"CREATE",Map.of("after",dto),actor,null);repository.clear();return id;
    }
    public int update(int id,ObjectNode dto,String actor) {
        Product existing=require(id,true);String key=ProductAdminInput.business(dto,existing);
        var before=ProductResponse.from(existing);repository.update(id,dto);repository.detail(id,key,dto);
        repository.audit(id,"UPDATE",Map.of("before",before,"after",dto),actor,null);repository.clear();return id;
    }
    public int stock(int id,int delta,String reason,String actor) {
        Product product=require(id,false);long next=(long)product.quantityInStock+delta;
        if(next<0) throw new CatalogException(400,"Stock adjustment cannot make stock negative");
        if(next>Integer.MAX_VALUE) throw new CatalogException(500,"Internal server error");
        repository.stock(id,(int)next);
        repository.audit(id,"STOCK_ADJUST",Map.of("productID",id,"from",product.quantityInStock,"to",next,"delta",delta),actor,reason);
        repository.clear();return id;
    }
    public Map<String,Object> batch(List<Integer> ids,boolean deactivate,String actor) {
        repository.lockManager(actor);
        var products=new HashMap<Integer,Product>();
        // Deterministic lock order prevents cycles between overlapping batches/managers.
        for(int id:ids.stream().sorted().toList()) { var product=repository.find(id,true,true);if(product!=null) products.put(id,product); }
        var day=LocalDate.now(ZoneId.systemDefault());var start=day.atStartOfDay(ZoneId.systemDefault()).toInstant();
        var end=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        if(repository.deleteCount(actor,start,end)+products.size()>20) throw new CatalogException(400,"Manager delete quota exceeded: maximum 20 products per day");
        var results=new ArrayList<Map<String,Object>>();
        for(int id:ids) {
            var product=products.get(id);if(product==null) { results.add(Map.of("id",id,"status","NOT_FOUND"));continue; }
            String status;
            if(deactivate || product.quantityInStock>0) status="DEACTIVATED";
            else status=repository.ordered(id)?"DEACTIVATED_ORDERED":"DELETED";
            if(status.equals("DELETED")) {
                repository.audit(id,"DELETE",Map.of("productID",id,"title",product.title,"productType",product.productType),actor,null);
                repository.status(id,"DELETED");
            } else {
                repository.status(id,"DEACTIVATED");
                repository.audit(id,"DEACTIVATE",Map.of("productID",id,"quantityInStock",product.quantityInStock,"status",status),actor,null);
            }
            results.add(Map.of("id",id,"status",status));
        }
        repository.clear();return Map.of("results",results);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> logs() { return repository.logs(); }
    private Product require(int id,boolean visible) {
        var product=repository.find(id,visible,true);
        if(product==null) throw new CatalogException(404,"Product with ID "+id+" not found");return product;
    }
}
