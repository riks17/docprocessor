package com.example.docprocessor.dto

import com.example.docprocessor.model.UserRole

data class LoginRequest(
    val username: String,
    val password: String
)

data class JwtResponse(
    val token: String,
    val username: String,
    val roles: List<String>
)

data class UserRegistrationRequest(
    val username: String,
    val password: String,
    val role: UserRole = UserRole.USER // Default to USER
)

data class MessageResponse(val message: String)