package com.hms.user.security;

import com.hms.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security UserDetails adapter over the HMS User entity.
 * Prefix "ROLE_" is required by Spring Security for role-based checks.
 */
@Getter
public class UserDetailsImpl implements UserDetails {

    private final Long   id;
    private final String email;
    private final String password;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(User user) {
        this.id          = user.getId();
        this.email       = user.getEmail();
        this.password    = user.getPasswordHash();
        this.active      = user.isActive();
        this.authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    // ── UserDetails contract ───────────────────────────────────────────────────

    @Override public String getUsername()                   { return email; }
    @Override public String getPassword()                   { return password; }
    @Override public boolean isAccountNonExpired()          { return true; }
    @Override public boolean isAccountNonLocked()           { return active; }
    @Override public boolean isCredentialsNonExpired()      { return true; }
    @Override public boolean isEnabled()                    { return active; }
}
