package vn.aims.product;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

@Entity @Table(name="product_logs")
public class ProductLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="log_id") Integer logID;
    @Column(name="action_type",nullable=false,length=20) String actionType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="changed_fields",columnDefinition="jsonb") JsonNode changedFields;
    @Column(name="performed_by",nullable=false,length=100) String performedBy;
    @Column(columnDefinition="text") String reason;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="product_id") Product product;
    protected ProductLog() { }
}
