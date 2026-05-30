package com.gongwen.assistant.organization;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserAdminService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]{3,80}");
    private static final Set<String> SUPPORTED_ROLES = Set.of("SYSTEM_ADMIN", "TEMPLATE_ADMIN", "DRAFTER");

    private final UserAdminRepository userAdminRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserAdminRepository userAdminRepository, PasswordEncoder passwordEncoder) {
        this.userAdminRepository = userAdminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserAdminDto> list() {
        return userAdminRepository.findAll();
    }

    public UserAdminDto create(CreateUserRequest request) {
        String username = normalizeUsername(request == null ? null : request.username());
        String displayName = normalizeDisplayName(request == null ? null : request.displayName());
        String password = normalizePassword(request == null ? null : request.password());
        List<String> roles = normalizeRoles(request == null ? null : request.roles());
        return userAdminRepository.create(
                username,
                displayName,
                passwordEncoder.encode(password),
                request == null ? null : request.departmentId(),
                roles);
    }

    public UserAdminDto update(long id, UpdateUserRequest request) {
        String displayName = normalizeDisplayName(request == null ? null : request.displayName());
        String status = normalizeStatus(request == null ? null : request.status());
        List<String> roles = normalizeRoles(request == null ? null : request.roles());
        return userAdminRepository.update(id, displayName, request == null ? null : request.departmentId(), status, roles)
                .orElseThrow(() -> new UserAdminException("USER_NOT_FOUND", "User not found"));
    }

    public void resetPassword(long id, ResetUserPasswordRequest request) {
        String password = normalizePassword(request == null ? null : request.password());
        if (!userAdminRepository.resetPassword(id, passwordEncoder.encode(password))) {
            throw new UserAdminException("USER_NOT_FOUND", "User not found");
        }
    }

    public void disable(long id) {
        if (!userAdminRepository.disable(id)) {
            throw new UserAdminException("USER_NOT_FOUND", "User not found");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UserAdminException("USERNAME_REQUIRED", "Username is required");
        }
        String normalized = username.strip();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new UserAdminException("USERNAME_INVALID", "Username must be 3-80 letters, numbers, dot, dash or underscore");
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new UserAdminException("DISPLAY_NAME_REQUIRED", "Display name is required");
        }
        return displayName.strip();
    }

    private String normalizePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new UserAdminException("PASSWORD_WEAK", "Password must be at least 8 characters");
        }
        return password;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.strip().toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) {
            throw new UserAdminException("USER_STATUS_INVALID", "User status is invalid");
        }
        return normalized;
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new UserAdminException("USER_ROLES_REQUIRED", "At least one role is required");
        }
        List<String> normalized = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.strip().toUpperCase())
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.stream().anyMatch(role -> !SUPPORTED_ROLES.contains(role))) {
            throw new UserAdminException("USER_ROLES_INVALID", "User roles are invalid");
        }
        return normalized;
    }
}
