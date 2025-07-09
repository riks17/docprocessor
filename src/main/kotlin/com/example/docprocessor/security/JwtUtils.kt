package com.example.docprocessor.security

import com.example.docprocessor.security.UserDetailsImpl // <-- ADD THIS IMPORT
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
// No longer need: import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtils {
    private val logger = LoggerFactory.getLogger(JwtUtils::class.java)

    @Value("\${jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${jwt.expirationMs}")
    private var jwtExpirationMs: Int = 0

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    fun generateJwtToken(authentication: Authentication): String {
        // --- THE FIX IS HERE ---
        // Cast to your custom UserDetailsImpl, not Spring's default User class.
        val userPrincipal = authentication.principal as UserDetailsImpl

        val roles = userPrincipal.authorities.map { it.authority }.toList()

        return Jwts.builder()
            .setSubject(userPrincipal.username)
            .claim("roles", roles) // This is good practice and remains unchanged.
            .setIssuedAt(Date())
            .setExpiration(Date(Date().time + jwtExpirationMs))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    // This function does not need changes as it's not part of the login flow's error.
    fun generateJwtTokenForUser(username: String, roles: List<String>): String {
        return Jwts.builder()
            .setSubject(username)
            .claim("roles", roles.map { "ROLE_$it"}) // Ensure ROLE_ prefix if not already there
            .setIssuedAt(Date())
            .setExpiration(Date(Date().time + jwtExpirationMs))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun getUserNameFromJwtToken(token: String): String {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body.subject
    }

    fun validateJwtToken(authToken: String): Boolean {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken)
            return true
        } catch (e: MalformedJwtException) {
            logger.error("Invalid JWT token: {}", e.message)
        } catch (e: ExpiredJwtException) {
            logger.error("JWT token is expired: {}", e.message)
        } catch (e: UnsupportedJwtException) {
            logger.error("JWT token is unsupported: {}", e.message)
        } catch (e: IllegalArgumentException) {
            logger.error("JWT claims string is empty: {}", e.message)
        }
        return false
    }
}