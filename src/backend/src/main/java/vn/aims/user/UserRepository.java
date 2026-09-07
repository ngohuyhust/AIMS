package vn.aims.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    @Override @EntityGraph(attributePaths = "roles")
    Optional<User> findById(Integer id);
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);
}
