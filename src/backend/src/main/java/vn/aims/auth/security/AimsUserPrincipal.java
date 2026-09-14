package vn.aims.auth.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Immutable account identity with erasable credentials for Spring Security authentication. */
public final class AimsUserPrincipal implements UserDetails, CredentialsContainer {
    private final Integer userID;
    private final String email;
    private final String fullName;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    private String password;

    public AimsUserPrincipal(Integer userID, String email, String fullName, String password,
            boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this.userID = userID;
        this.email = email;
        this.fullName = fullName;
        this.password = password;
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
    }

    public Integer userID() { return userID; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void eraseCredentials() { password = null; }
}
