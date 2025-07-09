package com.example.docprocessor.api

import com.example.docprocessor.dto.JwtResponse
import com.example.docprocessor.dto.LoginRequest
import com.example.docprocessor.dto.MessageResponse
import com.example.docprocessor.dto.SignupRequest
import com.example.docprocessor.model.User
import com.example.docprocessor.model.UserRole
import com.example.docprocessor.repository.UserRepository
import com.example.docprocessor.security.JwtUtils
import com.example.docprocessor.security.UserDetailsImpl // The correct class is imported
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
    fun authenticateUser(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<JwtResponse> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )

        SecurityContextHolder.getContext().authentication = authentication
        val jwt = jwtUtils.generateJwtToken(authentication)

        // --- THIS IS THE FIX ---
        // Cast to your custom UserDetails implementation, NOT the service.
        val userDetails = authentication.principal as UserDetailsImpl

        val roles = userDetails.getAuthorities().map { it.authority }

        return ResponseEntity.ok(
            JwtResponse(
                token = jwt,
                id = userDetails.getId(), // This will now resolve correctly
                username = userDetails.username,
                roles = roles
            )
        )
    }

    @PostMapping("/signup")
    fun registerUser(@Valid @RequestBody signupRequest: SignupRequest): ResponseEntity<*> {
        if (userRepository.existsByUsername(signupRequest.username)) {
            return ResponseEntity.badRequest().body(MessageResponse("Error: Username is already taken!"))
        }

        val user = User(
            username = signupRequest.username,
            password = passwordEncoder.encode(signupRequest.password),
            role = UserRole.USER
        )
        userRepository.save(user)

        return ResponseEntity.ok(MessageResponse("User registered successfully!"))
    }
}