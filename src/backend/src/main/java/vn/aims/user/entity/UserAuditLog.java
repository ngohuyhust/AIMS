package vn.aims.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "user_audit_logs")
public class UserAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id") private Integer logID;
    @Column(nullable = false, length = 100) private String action;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "performed_by", length = 50) private String performedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") private User user;

    protected UserAuditLog() { }
    public UserAuditLog(String action, String description, String performedBy, User user) {
        this.action = action;
        this.description = description;
        this.performedBy = performedBy;
        this.user = user;
    }
    @PrePersist void onCreate() { createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public Integer getLogID() { return logID; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public String getPerformedBy() { return performedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUser() { return user; }
}
