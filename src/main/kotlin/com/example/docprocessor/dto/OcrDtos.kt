package com.example.docprocessor.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents the standardized JSON response from the Python OCR microservice.
 * This single DTO is flexible enough to handle the output for all document types.
 *
 * @param documentType The type of document identified by the OCR service (e.g., "PAN_CARD", "VOTER_ID").
 * @param data A map containing the extracted key-value pairs from the document.
 *             Example for PAN: {"name": "John Doe", "dob": "15-08-1990", "pan_number": "ABCDE1234F"}
 * @param status The status of the OCR operation ("SUCCESS" or "FAILURE").
 * @param errorMessage An optional message describing the error if the status is "FAILURE".
 */

data class FastApiResponse(
    @JsonProperty("document_type")
    val documentType: String,
    val ocr: OcrDataContainer
)

data class OcrDataContainer(
    val fields: Any // Can be Map<String, String> or String for "Unknown"
)

data class OcrResponse(
    @JsonProperty("document_type")
    val documentType: String,

    @JsonProperty("data")
    val data: Map<String, String>,

    @JsonProperty("status")
    val status: String,

    @JsonProperty("error_message")
    val errorMessage: String? = null
)

/**
 * Represents the final, structured response sent from our Spring Boot API
 * back to the original client (e.g., a web or mobile front-end) after a
 * successful document upload and processing.
 *
 * @param documentId The unique ID of the saved document record in the database.
 * @param documentType The classified type of the document.
 * @param message A confirmation message for the client.
 */
data class DocumentProcessingResult(
    val documentId: Long,
    val documentType: String,
    val message: String
)