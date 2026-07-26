package com.example.moviebooking.dto;

import com.example.moviebooking.entity.Role;

// Deliberately excludes passwordHash - never echo any form of the password
// back to the client, hashed or not.
public record UserResponse(
        Long id,
        String email,
        String name,
        Role role
) {
}
