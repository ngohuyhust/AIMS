package vn.aims.user;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    List<Role> findByNameIn(Collection<String> names);
}
