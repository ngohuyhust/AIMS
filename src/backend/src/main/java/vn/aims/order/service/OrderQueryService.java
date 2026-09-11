package vn.aims.order.service;

import vn.aims.order.dto.OrderResponse;

import java.util.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.aims.order.repository.OrderRepository;

@Service
public class OrderQueryService {
    private final JdbcTemplate jdbc;private final OrderRepository orders;
    public OrderQueryService(JdbcTemplate jdbc,OrderRepository orders) {this.jdbc=jdbc;this.orders=orders;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(boolean refunds,int page,int limit,String search,String range,String method) {
        page=Math.max(1,page);limit=Math.min(30,Math.max(1,limit));var args=new ArrayList<Object>();
        String where=refunds?"o.status='REFUND_PENDING'":"o.status IN ('PENDING','PENDING_PROCESSING')";
        if(search!=null && !search.trim().isEmpty()) {
            search=search.trim();String pattern="%"+search.toLowerCase(Locale.ROOT)+"%";Long id=null;
            try {double n=Double.parseDouble(search.replaceFirst("^#",""));if(n>0 && n==Math.rint(n) && n<=Integer.MAX_VALUE) id=(long)n;}catch(NumberFormatException ignored){}
            where+=" AND ("+(id==null?"":"o.order_id=? OR ")+"lower(d.receiver_name) LIKE ? OR lower(d.email) LIKE ? OR d.phone_number LIKE ?)";
            if(id!=null)args.add(id);args.add(pattern);args.add(pattern);args.add(pattern);
        }
        var today=LocalDate.now();LocalDate start=switch(range==null?"ALL":range){case "TODAY"->today;case "WEEK"->today.minusDays(6);case "MONTH"->today.withDayOfMonth(1);default->null;};
        if(start!=null) {where+=" AND o.created_at>=?";args.add(java.sql.Timestamp.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));}
        if(refunds && !"VIETQR".equals(method)) method="ALL";
        if("UNPAID".equals(method)) where+=" AND NOT EXISTS(SELECT 1 FROM payment_transactions p WHERE p.order_id=o.order_id AND p.status='SUCCESS')";
        else if(method!=null && !method.equals("ALL")) {where+=" AND (SELECT method FROM payment_transactions p WHERE p.order_id=o.order_id AND p.status='SUCCESS' ORDER BY p.created_at DESC,p.transaction_id DESC LIMIT 1)=?";args.add(method);}
        String from=" FROM orders o LEFT JOIN delivery_info d ON d.order_id=o.order_id WHERE "+where;
        long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class,args.toArray());args.add(limit);args.add((long)(page-1)*limit);
        var ids=jdbc.queryForList("SELECT o.order_id"+from+" ORDER BY "+(refunds?"":"o.status DESC,")+"o.created_at ASC,o.order_id ASC LIMIT ? OFFSET ?",Integer.class,args.toArray());
        var items=ids.stream().map(id->{var item=OrderResponse.from(orders.find(id,false),false);String payment=orders.paymentMethod(id);item.put("paymentMethod",payment!=null?payment:refunds?"VIETQR":null);return item;}).toList();
        return Map.of("items",items,"total",total,"page",page,"limit",limit,"totalPages",(total+limit-1)/limit);
    }
}
