package vn.aims.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Integer> {
    java.util.List<UserAuditLog> findByUser_UserIDOrderByCreatedAtDesc(Integer userID,org.springframework.data.domain.Pageable pageable);
}
