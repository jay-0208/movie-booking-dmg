package com.example.moviebooking.controller;

import com.example.moviebooking.dto.RegisterRequest;
import com.example.moviebooking.dto.UserResponse;
import com.example.moviebooking.entity.Role;
import com.example.moviebooking.entity.User;
import com.example.moviebooking.exception.EmailAlreadyRegisteredException;
import com.example.moviebooking.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth/SSO/MFA are explicitly out of scope for this assignment - all that's
 * needed is basic auth with RBAC, which is already fully wired via
 * SecurityConfig + CustomUserDetailsService (see step 6). There is no
 * /api/auth/login endpoint on purpose: HTTP Basic doesn't need one - the
 * client sends the Authorization: Basic header directly on every request. All
 * self-registration here creates CUSTOMER users; there's deliberately no
 * public way to self-register as ADMIN - admin users are expected to be
 * seeded directly (e.g. a data.sql / CommandLineRunner) rather than exposed
 * as an API surface, which would be its own privilege-escalation risk.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyRegisteredException("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(Role.CUSTOMER)
                .build();
        user = userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
