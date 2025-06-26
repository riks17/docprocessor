package com.example.docprocessor.api

import com.example.docprocessor.dto.JwtResponse
import com.example.docprocessor.dto.LoginRequest
import com.example.docprocessor.dto.MessageResponse
import com.example.docprocessor.dto.UserRegistrationRequest
import com.example.docprocessor.model.User
import com.example.docprocessor.model.UserRole
import com.example.docprocessor.repository.UserRepository
import com.example.docprocessor.security.JwtUtils
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtils: JwtUtils
) {

    @PostMapping("/login")
    fun authenticateUser(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<*> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )
        SecurityContextHolder.getContext().authentication = authentication

        val userDetails = authentication.principal as org.springframework.security.core.userdetails.User
        val jwt = jwtUtils.generateJwtTokenForUser(userDetails.username, userDetails.authorities.map { it.authority })


        val roles = userDetails.authorities.map { it.authority }

        return ResponseEntity.ok(
            JwtResponse(
                token = jwt,
                username = userDetails.username,
                roles = roles
            )
        )
    }

    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody signUpRequest: UserRegistrationRequest): ResponseEntity<*> {
        if (userRepository.existsByUsername(signUpRequest.username)) {
            return ResponseEntity.badRequest().body(MessageResponse("Error: Username is already taken!"))
        }

        val user = User(
            username = signUpRequest.username,
            password = passwordEncoder.encode(signUpRequest.password),
            role = signUpRequest.role // Ensure role is correctly passed and handled
        )
        userRepository.save(user)
        return ResponseEntity.ok(MessageResponse("User registered successfully!"))
    }

    // Optional: Helper to create an admin user if none exists (e.g. for initial setup)
    // This is NOT secure for production, usually done via DB script or a secure setup process
    @PostMapping("/setup-admin")
    fun setupAdmin(@RequestBody adminRequest: UserRegistrationRequest): ResponseEntity<*> {
        if (adminRequest.username == "admin" && !userRepository.existsByUsername("admin")) {
            if (adminRequest.role != UserRole.ADMIN) {
                return ResponseEntity.badRequest().body(MessageResponse("Admin setup must specify ADMIN role."))
            }
            val adminUser = User(
                username = adminRequest.username,
                password = passwordEncoder.encode(adminRequest.password),
                role = UserRole.ADMIN
            )
            userRepository.save(adminUser)
            return ResponseEntity.ok(MessageResponse("Admin user created! Use this endpoint cautiously."))
        }
        return ResponseEntity.badRequest().body(MessageResponse("Admin already exists or invalid request."))
    }
}