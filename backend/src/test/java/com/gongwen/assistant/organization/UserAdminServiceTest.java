package com.gongwen.assistant.organization;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAdminServiceTest {
    private final InMemoryUserAdminRepository repository = new InMemoryUserAdminRepository();
    private final UserAdminService service = new UserAdminService(repository, new BCryptPasswordEncoder());

    @Test
    void createsUserWithEncodedPasswordAndRoles() {
        UserAdminDto created = service.create(new CreateUserRequest(
                " drafter ",
                " Drafter One ",
                "Plain@12345",
                1L,
                List.of("DRAFTER")));

        assertThat(created.username()).isEqualTo("drafter");
        assertThat(created.displayName()).isEqualTo("Drafter One");
        assertThat(created.roles()).containsExactly("DRAFTER");
        assertThat(repository.lastPasswordHash).startsWith("$2");
    }

    @Test
    void rejectsWeakPasswordWhenCreatingUser() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest(
                "drafter",
                "Drafter",
                "123",
                1L,
                List.of("DRAFTER"))))
                .isInstanceOf(UserAdminException.class)
                .hasMessageContaining("Password");
    }

    private static final class InMemoryUserAdminRepository implements UserAdminRepository {
        private String lastPasswordHash;
        private long nextId = 20L;

        @Override
        public List<UserAdminDto> findAll() {
            return new ArrayList<>();
        }

        @Override
        public UserAdminDto create(String username, String displayName, String passwordHash, Long departmentId, List<String> roles) {
            lastPasswordHash = passwordHash;
            return new UserAdminDto(nextId++, username, displayName, departmentId, "Head Office", "ACTIVE", roles);
        }

        @Override
        public Optional<UserAdminDto> update(long id, String displayName, Long departmentId, String status, List<String> roles) {
            return Optional.empty();
        }

        @Override
        public boolean resetPassword(long id, String passwordHash) {
            lastPasswordHash = passwordHash;
            return true;
        }

        @Override
        public boolean disable(long id) {
            return true;
        }
    }
}
