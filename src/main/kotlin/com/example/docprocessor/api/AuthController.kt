package com.example.docprocessor.api

import com.example.docprocessor.dto.*
import com.example.docprocessor.model.Role
import com.example.docprocessor.model.User
import com.example.docprocessor.repository.UserRepository
import com.example.docprocessor.security.JwtUtils
import com.example.docprocessor.security.UserDetailsImpl
import com.example.docprocessor.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
    private val jwtUtils: JwtUtils,
    private val userService: UserService
) {

    // ... (login method is fine) ...
    @PostMapping("/login")
    fun authenticateUser(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<*> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )
        SecurityContextHolder.getContext().authentication = authentication
        val jwt = jwtUtils.generateJwtToken(authentication)
        val userDetails = authentication.principal as UserDetailsImpl
        val roles = userDetails.authorities.map { it.authority }

        return ResponseEntity.ok(
            JwtResponse(token = jwt, id = userDetails.getId(), username = userDetails.username, roles = roles)
        )
    }


    @PostMapping("/signup")
    fun registerUser(@Valid @RequestBody signupRequest: SignupRequest): ResponseEntity<*> {
        // --- THE FIX ---
        // Instead of .isPresent, we use the repository's existsBy... method, which is cleaner.
        if (userRepository.existsByUsername(signupRequest.username)) {
            return ResponseEntity.badRequest().body(MessageResponse("Error: Username is already taken!"))
        }

        val user = User(
            username = signupRequest.username,
            password = passwordEncoder.encode(signupRequest.password),
            role = Role.USER
        )
        userRepository.save(user)
        return ResponseEntity.ok(MessageResponse("User registered successfully!"))
    }

    @PostMapping("/create-user")
    @PreAuthorize("hasAnyRole('SUPERADMIN')")
    fun createPrivilegedUser(@Valid @RequestBody request: PrivilegedUserCreateRequest): ResponseEntity<*> {
        try {
            val user = userService.createUserWithRole(request)
            return ResponseEntity.ok(MessageResponse("User '${user.username}' created with role ${user.role}"))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(MessageResponse(e.message ?: "An unknown error occurred."))
        }
    }
}