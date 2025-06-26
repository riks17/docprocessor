package com.example.docprocessor.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Enum representing the types of documents supported by the system.
 * UNKNOWN is used for cases where classification fails.
 */
enum class DocumentType {
    PAN_CARD,
    VOTER_ID,
    PASSPORT,
    UNKNOWN
}

/**
 * Enum representing the processing status of a document.
 */
enum class DocumentStatus {
    PENDING_VERIFICATION,
    REJECTED,
    UPLOADED,
    VERIFIED,
    FAILED_OCR
}

/**
 * Entity to log every document upload transaction.
 */
@Entity
@Table(name = "document_log")
data class DocumentLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "uploader", nullable = false)
    var uploader: String, // Username of the uploader

    @Column(name = "file_name", nullable = false)
    var fileName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    var docType: DocumentType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: DocumentStatus,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)