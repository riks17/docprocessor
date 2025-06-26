package com.example.docprocessor.api

import com.example.docprocessor.model.PanData
import com.example.docprocessor.model.PassportData
import com.example.docprocessor.model.VoterIdData
import com.example.docprocessor.repository.PanDataRepository
import com.example.docprocessor.repository.PassportDataRepository
import com.example.docprocessor.repository.VoterIdDataRepository
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
    private val documentProcessingService: DocumentProcessingService,
    private val panDataRepository: PanDataRepository,
    private val voterIdDataRepository: VoterIdDataRepository,
    private val passportDataRepository: PassportDataRepository
) {
    private val logger = LoggerFactory.getLogger(DocumentController::class.java)

    /**
     * Endpoint to upload a document (JPG, PNG, PDF) for processing.
     * Accessible by users with 'USER' or 'ADMIN' roles.
     */
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
            logger.info("File '${file.originalFilename}' processed successfully for user '${userDetails.username}'")
            return ResponseEntity.ok(result)
        } catch (e: Exception) {
            logger.error("Error processing file '${file.originalFilename}' for user '${userDetails.username}': ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "Error processing document: ${e.message}"))
        }
    }

    /**
     * Endpoint to get processed PAN Card data by its database ID.
     * Accessible by users with 'ADMIN' role only.
     */
    @GetMapping("/pan/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPanDataById(@PathVariable id: Long): ResponseEntity<*> {
        logger.info("Admin request to fetch PAN Card data with ID: $id")
        val panData: PanData? = panDataRepository.findById(id).orElse(null)
        return if (panData != null) {
            ResponseEntity.ok(panData)
        } else {
            logger.warn("PAN Card data with ID: $id not found")
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "PAN Card data with ID $id not found"))
        }
    }

    /**
     * Endpoint to get processed Voter ID data by its database ID.
     * Accessible by users with 'ADMIN' role only.
     */
    @GetMapping("/voter-id/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getVoterIdDataById(@PathVariable id: Long): ResponseEntity<*> {
        logger.info("Admin request to fetch Voter ID data with ID: $id")
        val voterIdData: VoterIdData? = voterIdDataRepository.findById(id).orElse(null)
        return if (voterIdData != null) {
            ResponseEntity.ok(voterIdData)
        } else {
            logger.warn("Voter ID data with ID: $id not found")
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Voter ID data with ID $id not found"))
        }
    }

    /**
     * Endpoint to get processed Passport data by its database ID.
     * Accessible by users with 'ADMIN' role only.
     */
    @GetMapping("/passport/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPassportDataById(@PathVariable id: Long): ResponseEntity<*> {
        logger.info("Admin request to fetch Passport data with ID: $id")
        val passportData: PassportData? = passportDataRepository.findById(id).orElse(null)
        return if (passportData != null) {
            ResponseEntity.ok(passportData)
        } else {
            logger.warn("Passport data with ID: $id not found")
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Passport data with ID $id not found"))
        }
    }
}