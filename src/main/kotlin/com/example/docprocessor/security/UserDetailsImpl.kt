package com.example.docprocessor.security

import com.example.docprocessor.model.User
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserDetailsImpl(private val user: User) : UserDetails {

    fun getId(): Long {
        return user.id!!
    }

    /**
     * This is the critical change.
     * It takes the clean role name from the database (e.g., "ADMIN") and adds the "ROLE_"
     * prefix before giving it to Spring Security.
     * This makes @PreAuthorize("hasRole('ADMIN')") work correctly.
     */
    override fun getAuthorities(): Collection<GrantedAuthority> {
        val authority = SimpleGrantedAuthority("ROLE_${user.role.name}")
        return listOf(authority)
    }

    @JsonIgnore
    override fun getPassword(): String {
        return user.password
    }

    override fun getUsername(): String {
        return user.username
    }

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}