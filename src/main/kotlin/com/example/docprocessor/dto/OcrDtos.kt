package com.example.docprocessor.dto

import com.fasterxml.jackson.annotation.JsonProperty

// For FastAPI Response
data class FastApiResponse(
    @JsonProperty("document_type")
    val documentType: String,
    val ocr: OcrDataContainer
)

data class OcrDataContainer(
    val fields: Any // Can be Map<String, String> or String for "Unknown"
)

// Specific field structures for easier parsing after type check
data class AadhaarOcrFields(
    @JsonProperty("date_of_birth-year")
    val dateOfBirthYear: String?, // Expecting "dd/MM/yyyy" or just "yyyy" as per your example (table has date)
    @JsonProperty("aadhar_number")
    val aadharNumber: String?,
    val gender: String?,
    val name: String?
)

data class DrivingLicenseOcrFields(
    @JsonProperty("license_number")
    val licenseNumber: String?,
    @JsonProperty("expiry_date")
    val expiryDate: String?, // Expecting "dd/MM/yyyy"
    val name: String?
)