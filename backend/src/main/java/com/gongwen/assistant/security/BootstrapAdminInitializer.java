package com.gongwen.assistant.security;

import com.gongwen.assistant.organization.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public BootstrapAdminInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${gongwen.security.bootstrap-admin.username:admin}") String bootstrapUsername,
            @Value("${gongwen.security.bootstrap-admin.password:}") String bootstrapPassword
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userAccountRepository.countUsers() > 0) {
            return;
        }
        String password = bootstrapPassword == null || bootstrapPassword.isBlank()
                ? generatedPassword()
                : bootstrapPassword;
        long rootDepartmentId = userAccountRepository.rootDepartmentId();
        userAccountRepository.createUser(
                bootstrapUsername,
                "System Admin",
                passwordEncoder.encode(password),
                rootDepartmentId,
                List.of("SYSTEM_ADMIN", "TEMPLATE_ADMIN", "DRAFTER"));
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("Created bootstrap admin user '{}' with one-time local password: {}", bootstrapUsername, password);
        } else {
            log.info("Created bootstrap admin user '{}'", bootstrapUsername);
        }
    }

    private String generatedPassword() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        return "Gongwen@" + HexFormat.of().formatHex(bytes);
    }
}
