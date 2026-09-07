package vn.aims.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Integer> { }
