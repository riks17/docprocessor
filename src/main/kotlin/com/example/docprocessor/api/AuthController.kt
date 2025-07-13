package com.example.docprocessor.api

import com.example.docprocessor.dto.*
import com.example.docprocessor.model.User
import com.example.docprocessor.repository.UserRepository
import com.example.docprocessor.security.JwtUtils
import com.example.docprocessor.security.UserDetailsImpl
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

    // Login endpoint remains unchanged.
    @PostMapping("/login")
    fun authenticateUser(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<JwtResponse> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )

        SecurityContextHolder.getContext().authentication = authentication
        val jwt = jwtUtils.generateJwtToken(authentication)

        val userDetails = authentication.principal as UserDetailsImpl
        val roles = userDetails.authorities.map { it.authority }

        return ResponseEntity.ok(
            JwtResponse(
                token = jwt,
                id = userDetails.getId(),
                username = userDetails.username,
                roles = userDetails.
            )
        )
    }

    /**
     * Unified, public signup endpoint that directly assigns the provided role.
     * WARNING: DEVELOPMENT ONLY.
     */
    @PostMapping("/signup")
    fun registerUser(@Valid @RequestBody signupRequest: SignupRequest): ResponseEntity<*> {
        if (userRepository.findByUsername(signupRequest.username).isPresent) {
            return ResponseEntity.badRequest().body(MessageResponse("Error: Username is already taken!"))
        }

        // --- DIRECT ASSIGNMENT - NO IF/ELSE ---
        val user = User(
            username = signupRequest.username,
            password = passwordEncoder.encode(signupRequest.password),
            role = signupRequest.role // The role is taken directly from the request.
        )
        userRepository.save(user)

        return ResponseEntity.ok(MessageResponse("User registered successfully with role: ${user.role.name}"))
    }
}