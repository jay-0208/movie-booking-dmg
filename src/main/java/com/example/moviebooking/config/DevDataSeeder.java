package com.example.moviebooking.config;

import com.example.moviebooking.entity.Role;
import com.example.moviebooking.entity.User;
import com.example.moviebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds exactly one ADMIN user on startup, only if none exists yet.
 *
 * Why this exists: AuthController.register() only ever creates CUSTOMER users
 * (see the comment there on why self-registering as ADMIN would be a
 * privilege-escalation hole), which means without this seeder there is no way
 * to reach any /api/admin/** endpoint on a fresh database at all - nobody
 * could even create the first City/Movie/Show to demo the app.
 *
 * This is a dev/demo convenience, not a production pattern - call this out in
 * your video. A real system would provision the first admin out-of-band
 * (ops runbook, a one-time migration, a separate internal tool), not bake
 * default credentials into the app itself.
 */
@Component
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private static final String DEFAULT_ADMIN_EMAIL = "admin@moviebooking.local";
    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail(DEFAULT_ADMIN_EMAIL).isPresent()) {
            return;
        }

        User admin = User.builder()
                .email(DEFAULT_ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .name("Default Admin")
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        System.out.println("=================================================================");
        System.out.println("Seeded default admin user for local/demo use:");
        System.out.println("  email:    " + DEFAULT_ADMIN_EMAIL);
        System.out.println("  password: " + DEFAULT_ADMIN_PASSWORD);
        System.out.println("Use these with HTTP Basic auth against /api/admin/** endpoints.");
        System.out.println("=================================================================");
    }
}
