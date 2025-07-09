package com.example.docprocessor.security

import com.example.docprocessor.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserDetailsServiceImpl(private val userRepository: UserRepository) : UserDetailsService {

    /**
     * Locates the user based on the username.
     * This method is called by the DaoAuthenticationProvider during the authentication process.
     */
    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        // 1. Fetch your custom User entity from the database.
        val user = userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User Not Found with username: $username") }

        // 2. Wrap it in your custom UserDetailsImpl class.
        // This is much cleaner and more powerful than using Spring's generic User class.
        return UserDetailsImpl(user)
    }
}