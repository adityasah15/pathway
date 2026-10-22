package com.tcet.RestApi.config;

import com.tcet.RestApi.model.User;
import com.tcet.RestApi.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the first accounts so a fresh database can actually be logged into.
 * Disabled for tests, and only runs when the users table is empty.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.viewer.username:viewer}")
    private String viewerUsername;

    @Value("${app.viewer.password:}")
    private String viewerPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        boolean seeded = seed(adminUsername, adminPassword, "ADMIN");
        seeded |= seed(viewerUsername, viewerPassword, "VIEWER");

        if (seeded) {
            log.warn("Seeded development accounts. Override ADMIN_PASSWORD and VIEWER_PASSWORD before any real use.");
        }
    }

    private boolean seed(String username, String password, String role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return false;
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);
        log.info("Seeded {} account '{}'", role, username);
        return true;
    }
}
