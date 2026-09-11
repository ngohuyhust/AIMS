package vn.aims.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.aims.user.entity.UserAuditLog;

public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Integer> {
    java.util.List<UserAuditLog> findByUser_UserIDOrderByCreatedAtDesc(Integer userID,org.springframework.data.domain.Pageable pageable);
}
