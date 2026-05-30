package com.gongwen.assistant.security;

import com.gongwen.assistant.organization.UserAccountRecord;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class GongwenUserPrincipal implements UserDetails {
    private final CurrentUser currentUser;
    private final String passwordHash;

    private GongwenUserPrincipal(CurrentUser currentUser, String passwordHash) {
        this.currentUser = currentUser;
        this.passwordHash = passwordHash;
    }

    public static GongwenUserPrincipal from(UserAccountRecord record) {
        return new GongwenUserPrincipal(
                new CurrentUser(
                        record.id(),
                        record.username(),
                        record.displayName(),
                        record.departmentId(),
                        record.departmentName(),
                        record.roles()),
                record.passwordHash());
    }

    public CurrentUser currentUser() {
        return currentUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return currentUser.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return currentUser.username();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
