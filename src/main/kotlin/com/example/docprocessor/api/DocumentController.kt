package com.example.docprocessor.api

import com.example.docprocessor.service.DocumentProcessingService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentProcessingService: DocumentProcessingService
) {
    private val logger = LoggerFactory.getLogger(DocumentController::class.java)

    /**
     * Endpoint to upload a document for asynchronous processing.
     * Accessible by USER and ADMIN roles.
     * This is a suspend function to support the non-blocking service call.
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    suspend fun uploadAndProcessDocument(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<*> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("message" to "File cannot be empty"))
        }

        // The service now handles all logic and potential exceptions.
        // A GlobalExceptionHandler will catch and format errors for a clean response.
        logger.info("User '${userDetails.username}' attempting to upload file: '${file.originalFilename}'")
        val result = documentProcessingService.processUploadedDocument(file, userDetails.username)
        logger.info("File '${file.originalFilename}' processed successfully for user '${userDetails.username}'")
        return ResponseEntity.ok(result)
    }

    /**
     * Retrieves PAN Card data by ID. Admin only.
     */
    @GetMapping("/pan/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPanDataById(@PathVariable id: Long): ResponseEntity<*> {
        val data = documentProcessingService.getPanDataById(id)
        return if (data != null) {
            ResponseEntity.ok(data)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to "PAN Card data with ID $id not found"))
        }
    }

    /**
     * Retrieves Voter ID data by ID. Admin only.
     */
    @GetMapping("/voter-id/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getVoterIdDataById(@PathVariable id: Long): ResponseEntity<*> {
        val data = documentProcessingService.getVoterIdDataById(id)
        return if (data != null) {
            ResponseEntity.ok(data)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to "Voter ID data with ID $id not found"))
        }
    }

    /**
     * Retrieves Passport data by ID. Admin only.
     */
    @GetMapping("/passport/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPassportDataById(@PathVariable id: Long): ResponseEntity<*> {
        val data = documentProcessingService.getPassportDataById(id)
        return if (data != null) {
            ResponseEntity.ok(data)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to "Passport data with ID $id not found"))
        }
    }
}