package com.example.docprocessor.dto

import com.example.docprocessor.model.UserRole // Import the new Role enum
import jakarta.validation.constraints.NotBlank

data class LoginRequest(val username: String, val password: String)

data class SignupRequest(
    @field:NotBlank
    val username: String,

    @field:NotBlank
    val password: String,

    // This field now directly accepts the Role enum.
    // Jackson will handle the string-to-enum conversion.
    val role: UserRole
)

data class JwtResponse(
    val token: String,
    val id: Long,
    val username: String,
    val roles: String
)

data class MessageResponse(val message: String)