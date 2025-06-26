package com.example.docprocessor.model

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

enum class UserRole {
    USER, ADMIN
}

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    @NotBlank
    var username: String,

    @Column(nullable = false)
    @NotBlank
    var password: String, // Store hashed password

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole
)