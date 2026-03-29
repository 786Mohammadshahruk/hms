package com.hms.appointment.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Lightweight UserDetails derived from JWT claims.
 * No DB lookup required — we trust the validated token.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long   userId;
    private final String email;
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long userId, String email, String role) {
        this.userId      = userId;
        this.email       = email;
        this.role        = role;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getUsername()              { return email; }
    @Override public String getPassword()              { return null; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
