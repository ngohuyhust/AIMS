package vn.aims.vietqr.repository;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.aims.payment.exception.PaymentException;

@Repository
public class VietqrStore {
    public record Row(int id,int paymentId,int orderId,BigDecimal amount,String content,String qrCode,String qrLink,String transactionId,String reference,Instant expires,String status,Instant created) {}
    private final JdbcTemplate jdbc;
    private final org.springframework.jdbc.core.RowMapper<Row> mapper=(r,n)->new Row(r.getInt("vietqr_transaction_id"),r.getInt("transaction_id"),r.getInt("order_id"),r.getBigDecimal("amount"),r.getString("content"),r.getString("qr_code"),r.getString("qr_link"),r.getString("transaction_id_ref"),r.getString("transaction_ref_id"),r.getTimestamp("expired_at").toInstant(),r.getString("status"),r.getTimestamp("created_at").toInstant());
    public VietqrStore(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public String lockOrder(int id) {
        var tokens=jdbc.query("SELECT customer_access_token FROM orders WHERE order_id=? FOR UPDATE",(r,n)->r.getString(1),id);
        if(tokens.isEmpty()) throw new PaymentException(404,"Order not found");return tokens.getFirst();
    }
    public Row byPayment(int id) {var rows=jdbc.query("SELECT * FROM vietqr_transactions WHERE transaction_id=?",mapper,id);return rows.isEmpty()?null:rows.getFirst();}
    public List<Row> byReference(String ref) {return jdbc.query("SELECT * FROM vietqr_transactions WHERE transaction_ref_id=?",mapper,ref);}
    public List<Row> byDetails(int order,String content,BigDecimal amount) {return jdbc.query("SELECT * FROM vietqr_transactions WHERE order_id=? AND content=? AND amount=?",mapper,order,content,amount);}
    public List<Row> pending(int order) {return jdbc.query("SELECT * FROM vietqr_transactions WHERE order_id=? AND status='PENDING'",mapper,order);}
    public void placeholder(int payment,int order,BigDecimal amount,String content,Instant expires) {
        jdbc.update("INSERT INTO vietqr_transactions(transaction_id,order_id,amount,content,expired_at) VALUES (?,?,?,?,?) ON CONFLICT(transaction_id) DO NOTHING",payment,order,amount,content,java.sql.Timestamp.from(expires));
    }
    public void generated(int id,JsonNode qr) {
        jdbc.update("UPDATE vietqr_transactions SET qr_code=?,qr_link=?,transaction_id_ref=?,transaction_ref_id=?,updated_at=now() WHERE vietqr_transaction_id=?",nullable(qr,"qrCode"),nullable(qr,"qrLink"),nullable(qr,"transactionId"),nullable(qr,"transactionRefId"),id);
    }
    private String nullable(JsonNode data,String field) {return data.path(field).isNull()?null:data.path(field).asText();}
    public void expire(Row row) {jdbc.update("UPDATE vietqr_transactions SET status='EXPIRED',updated_at=now() WHERE vietqr_transaction_id=? AND status='PENDING'",row.id());}
    public void receipt(String bank,String bankId,int payment) {
        jdbc.update("INSERT INTO vietqr_receipts(bank_account,bank_transaction_id,transaction_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",bank,bankId,payment);
        var ids=jdbc.queryForList("SELECT transaction_id FROM vietqr_receipts WHERE bank_account=? AND bank_transaction_id=?",Integer.class,bank,bankId);
        if(ids.size()!=1 || ids.getFirst()!=payment) throw new PaymentException(409,"VietQR bank receipt is already assigned or payment has another receipt");
    }
    public void paid(Row row,JsonNode callback) {
        var raw=((com.fasterxml.jackson.databind.node.ObjectNode)callback).deepCopy();raw.remove(List.of("bankaccount","sign"));
        jdbc.update("UPDATE vietqr_transactions SET status='PAID',transaction_id_ref=?,transaction_ref_id=?,paid_at=?,raw_callback=?::jsonb,updated_at=now() WHERE vietqr_transaction_id=? AND status='PENDING'",callback.path("transactionid").asText(),callback.path("referencenumber").asText().trim(),java.sql.Timestamp.from(Instant.ofEpochMilli(callback.path("transactiontime").asLong())),raw.toString(),row.id());
    }
}
