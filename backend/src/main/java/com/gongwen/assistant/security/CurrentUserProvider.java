package com.gongwen.assistant.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return toCurrentUser(authentication);
    }

    public AuthUserDto toDto(Authentication authentication) {
        CurrentUser user = toCurrentUser(authentication);
        return new AuthUserDto(
                user.id(),
                user.username(),
                user.displayName(),
                user.departmentId(),
                user.departmentName(),
                user.roles());
    }

    private CurrentUser toCurrentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof GongwenUserPrincipal userPrincipal) {
            return userPrincipal.currentUser();
        }
        throw new AuthenticationCredentialsNotFoundException("Unsupported authentication principal");
    }
}
