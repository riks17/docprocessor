package com.example.docprocessor.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO that exactly matches the root JSON object from the Python FastAPI service.
 * e.g., {"results": [...]}
 */
data class PythonOcrResponse(
    val results: List<PythonOcrResult>
)

/**
 * DTO that matches each object inside the "results" list.
 */
data class PythonOcrResult(
    val filename: String,
    @JsonProperty("document_type")
    val documentType: String,
    @JsonProperty("ocr_results")
    val ocrResults: Map<String, String>? = null, // Can be null in case of error
    val message: String? = null,
    val error: String? = null
)

/**
 * Represents the final, structured response sent from our Spring Boot API
 * back to the original client (e.g., a web or mobile front-end) after a
 * successful document upload and processing.
 */
data class DocumentProcessingResult(
    val documentId: Long,
    val documentType: String,
    val message: String
)

// Add these DTOs for the AuthController
