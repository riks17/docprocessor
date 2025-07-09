package com.example.docprocessor.security

import com.example.docprocessor.model.User
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserDetailsImpl(private val user: User) : UserDetails {

    fun getId(): Long {
        // Safe to use non-null assertion because a user from the DB will always have an ID.
        return user.id!!
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        // Spring Security expects the "ROLE_" prefix for role-based authorization.
        val authority = SimpleGrantedAuthority("ROLE_${user.role.name}")
        return listOf(authority)
    }

    @JsonIgnore // Prevent the password from being serialized into JSON responses.
    override fun getPassword(): String {
        return user.password
    }

    override fun getUsername(): String {
        return user.username
    }

    // For this project, we can assume accounts are always active.
    // These would be based on fields in your User model in a more complex app.
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}