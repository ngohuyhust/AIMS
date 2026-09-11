package vn.aims.user.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.aims.user.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    List<Role> findByNameIn(Collection<String> names);
}
