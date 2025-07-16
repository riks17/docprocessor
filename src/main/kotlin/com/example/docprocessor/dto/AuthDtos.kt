package com.example.docprocessor.dto

import com.example.docprocessor.model.Role
import jakarta.validation.constraints.NotBlank

data class LoginRequest(@field:NotBlank val username: String, @field:NotBlank val password: String)

/**
 * For the PUBLIC signup endpoint. It has no role field for security.
 */
data class SignupRequest(@field:NotBlank val username: String, @field:NotBlank val password: String)

/**
 * For the SECURED user creation endpoint, used only by admins. Allows specifying a role.
 */
data class PrivilegedUserCreateRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    val role: Role // Can be ROLE_USER or ROLE_ADMIN
)

data class JwtResponse(
    val token: String,
    val id: Long,
    val username: String,
    val roles: List<String> // Correctly typed as a List
)

data class MessageResponse(val message: String)