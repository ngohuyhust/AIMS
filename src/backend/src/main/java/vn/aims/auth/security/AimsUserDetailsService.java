package vn.aims.auth.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.user.repository.UserRepository;

@Service
public class AimsUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AimsUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = users.findByEmail(email == null ? "" : email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
        return new AimsUserPrincipal(user.getUserID(), user.getEmail(), user.getFullName(),
                user.getPasswordHash(), "ACTIVE".equals(user.getStatus()), authorities);
    }
}
