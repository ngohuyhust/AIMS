package vn.aims.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") private Integer userID;
    @Column(nullable = false, length = 100, unique = true) String email;
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 255) String fullName;
    @Column(name = "phone_number", length = 20) String phoneNumber;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @ManyToMany
    @JoinTable(name = "users_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() { }

    public User(String email, String passwordHash, String fullName, String phoneNumber, Collection<Role> roles) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.roles.addAll(roles);
    }

    @PrePersist void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public Integer getUserID() { return userID; }
    public String getEmail() { return email; }
    @JsonIgnore public String getPasswordHash() { return passwordHash; }
    public void replacePasswordHash(String encodedHash) { passwordHash = java.util.Objects.requireNonNull(encodedHash); }
    public String getFullName() { return fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return java.util.Collections.unmodifiableSet(roles); }
    void replaceRoles(Collection<Role> replacements) { roles.clear(); roles.addAll(replacements); }
}
