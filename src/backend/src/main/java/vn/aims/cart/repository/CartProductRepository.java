package vn.aims.cart.repository;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Narrow read projection, without loading catalog subtype graphs or creating cart persistence. */
@Repository
public class CartProductRepository {
    public record Product(int id,String status,int stock,BigDecimal price,double weight) {}
    private final NamedParameterJdbcTemplate jdbc;
    public CartProductRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<Integer,Product> find(Collection<Integer> ids) {
        var result=new HashMap<Integer,Product>();
        jdbc.query("SELECT product_id,status,quantity_in_stock,current_price,weight FROM products WHERE product_id IN (:ids)",Map.of("ids",ids),
                (org.springframework.jdbc.core.RowCallbackHandler) row->{
                    int id=row.getInt("product_id");
                    result.put(id,new Product(id,row.getString("status"),row.getInt("quantity_in_stock"),row.getBigDecimal("current_price"),row.getDouble("weight")));
                });
        return result;
    }
}
