package vn.aims.user;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal account lookups; HTTP administration and authentication belong to later modules. */
@Service
@Transactional(readOnly = true)
public class UserDomainService {
    private final UserRepository users;
    public UserDomainService(UserRepository users) { this.users = users; }
    public Optional<User> findById(Integer userID) { return users.findById(userID); }
    public Optional<User> findByEmail(String email) { return users.findByEmail(email); }
}
