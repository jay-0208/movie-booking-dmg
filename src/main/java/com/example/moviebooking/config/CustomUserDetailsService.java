package com.example.moviebooking.config;

import com.example.moviebooking.entity.User;
import com.example.moviebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges our User entity to Spring Security. Without this bean, Spring Boot
 * falls back to a single auto-generated in-memory user with no roles at all -
 * meaning hasRole("ADMIN") in SecurityConfig would never actually pass for
 * anyone. This is what makes RBAC real instead of decorative.
 *
 * Username here is email (matches Authentication#getName() usage in
 * BookingController.currentUserId()). Role -> authority mapping is
 * "ROLE_" + enum name, which is what Spring Security's hasRole("ADMIN")
 * expects under the hood (hasRole automatically prefixes "ROLE_").
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .build();
    }
}
