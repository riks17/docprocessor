package com.example.docprocessor.config

import com.example.docprocessor.model.Role
import com.example.docprocessor.model.User
import com.example.docprocessor.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SuperAdminInitializer(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.superadmin.username}") private val superAdminUsername: String,
    @Value("\${app.superadmin.password}") private val superAdminPassword: String
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(SuperAdminInitializer::class.java)

    /**
     * This method runs once the application context is loaded.
     * It checks if a SuperAdmin exists and creates one if it doesn't.
     */
    override fun run(vararg args: String?) {
        if (userRepository.findFirstByRole(Role.SUPERADMIN).isEmpty) {
            logger.info("No SuperAdmin account found. Creating a default SuperAdmin user.")

            val superAdmin = User(
                username = superAdminUsername,
                password = passwordEncoder.encode(superAdminPassword),
                role = Role.SUPERADMIN
            )
            userRepository.save(superAdmin)

            // Log the credentials in a highly visible way
            logger.warn("=================================================================")
            logger.warn("  >>> Default SuperAdmin Account Created <<<")
            logger.warn("  Username: $superAdminUsername")
            logger.warn("  Password: $superAdminPassword")
            logger.warn("  Please change this password immediately in a production environment.")
            logger.warn("=================================================================")
        } else {
            logger.info("SuperAdmin account already exists. Skipping creation.")
        }
    }
}