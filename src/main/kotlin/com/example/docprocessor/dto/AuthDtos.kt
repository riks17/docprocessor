package com.example.docprocessor.dto

import jakarta.validation.constraints.NotBlank


data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String)

data class SignupRequest(
    @NotBlank
    val username: String,

    @NotBlank
    val password: String
)

data class JwtResponse(
    val token: String,
    val id: Long,
    val username: String,
    val roles: List<String>
)

data class MessageResponse(val message: String)