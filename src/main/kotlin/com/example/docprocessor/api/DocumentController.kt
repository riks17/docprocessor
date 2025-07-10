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
    // The controller only needs to know about the service layer.
    private val documentProcessingService: DocumentProcessingService
) {
    private val logger = LoggerFactory.getLogger(DocumentController::class.java)

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    fun uploadAndProcessDocument(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<*> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("message" to "File cannot be empty"))
        }
        try {
            logger.info("User '${userDetails.username}' attempting to upload file: '${file.originalFilename}'")
            val result = documentProcessingService.processUploadedDocument(file, userDetails.username)
            return ResponseEntity.ok(result)
        } catch (e: Exception) {
            // It's good practice to catch exceptions from the service and return a clean error
            logger.error("Error processing file for user '${userDetails.username}': ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "Error processing document: ${e.message}"))
        }
    }

    @GetMapping("/pan/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPanDataById(@PathVariable id: Long): ResponseEntity<*> {
        val panData = documentProcessingService.getPanDataById(id)
        return if (panData != null) {
            ResponseEntity.ok(panData)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "PAN Card data with ID $id not found"))
        }
    }

    @GetMapping("/voter-id/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getVoterIdDataById(@PathVariable id: Long): ResponseEntity<*> {
        val voterIdData = documentProcessingService.getVoterIdDataById(id)
        return if (voterIdData != null) {
            ResponseEntity.ok(voterIdData)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Voter ID data with ID $id not found"))
        }
    }

    @GetMapping("/passport/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPassportDataById(@PathVariable id: Long): ResponseEntity<*> {
        // This is the line from your screenshot, it will now work.
        val passportData = documentProcessingService.getPassportDataById(id)
        return if (passportData != null) {
            ResponseEntity.ok(passportData)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Passport data with ID $id not found"))
        }
    }
}