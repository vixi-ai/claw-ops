package com.openclaw.manager.openclawserversmanager.users.config;

import com.openclaw.manager.openclawserversmanager.users.entity.Role;
import com.openclaw.manager.openclawserversmanager.users.entity.User;
import com.openclaw.manager.openclawserversmanager.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_EMAIL:}") String adminEmail,
            @Value("${ADMIN_USERNAME:}") String adminUsername,
            @Value("${ADMIN_PASSWORD:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Users already exist, skipping admin bootstrap");
            return;
        }

        if (adminEmail.isBlank() || adminUsername.isBlank() || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL, ADMIN_USERNAME, or ADMIN_PASSWORD not set — skipping admin bootstrap");
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        userRepository.save(admin);
        log.info("Bootstrap admin user created: {}", adminEmail);
    }
}
