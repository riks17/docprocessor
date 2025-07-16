package com.example.docprocessor.service

import com.example.docprocessor.dto.PrivilegedUserCreateRequest
import com.example.docprocessor.model.Role
import com.example.docprocessor.model.User
import com.example.docprocessor.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun createUserWithRole(request: PrivilegedUserCreateRequest): User {
        // --- THE FIX ---
        // Use the clean `existsByUsername` method provided by the repository.
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Error: Username '${request.username}' is already taken!")
        }

        val user = User(
            username = request.username,
            password = passwordEncoder.encode(request.password),
            role = request.role
        )
        return userRepository.save(user)
    }
}