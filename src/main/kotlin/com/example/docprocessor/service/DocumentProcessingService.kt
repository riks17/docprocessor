package com.example.docprocessor.service

import com.example.docprocessor.dto.AadhaarOcrFields
import com.example.docprocessor.dto.DrivingLicenseOcrFields
import com.example.docprocessor.dto.FastApiResponse
import com.example.docprocessor.model.*
import com.example.docprocessor.repository.PanDataRepository
import com.example.docprocessor.repository.DocumentLogRepository
import com.example.docprocessor.repository.PassportDataRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class DocumentProcessingService(
    private val fileStorageService: FileStorageService,
    private val pdfConversionService: PdfConversionService,
    private val ocrClientService: OcrClientService,
    private val panDataRepository: PanDataRepository,
    private val passportDataRepository: PassportDataRepository,
    private val documentLogRepository: DocumentLogRepository,
    private val objectMapper: ObjectMapper // For converting Map to DTO
) {
    private val logger = LoggerFactory.getLogger(DocumentProcessingService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")


    @Transactional
    fun processUploadedDocument(multipartFile: MultipartFile, username: String): Any {
        val (originalStoredFile, uniqueFilename) = fileStorageService.storeFile(multipartFile)
        var fileToProcess: File = originalStoredFile
        var isPdfConverted = false

        if (multipartFile.contentType == "application/pdf" || originalStoredFile.extension.equals("pdf", ignoreCase = true)) {
            logger.info("PDF file detected: ${originalStoredFile.name}. Converting to image.")
            // Ensure output directory exists for converted images, could be same as uploadDir or a sub-directory
            val conversionOutputDir = originalStoredFile.parentFile
            fileToProcess = pdfConversionService.convertPdfToImage(originalStoredFile, conversionOutputDir)
            isPdfConverted = true
            logger.info("PDF converted to image: ${fileToProcess.name}")
        }

        val fastApiResponse: FastApiResponse
        try {
            fastApiResponse = ocrClientService.callFastApiProcessDocument(fileToProcess).block()
                ?: throw RuntimeException("Failed to get response from OCR service")
        } catch (e: Exception) {
            logger.error("Error calling OCR service: ${e.message}", e)
            logDocument(username, uniqueFilename, DocumentType.UNKNOWN, DocumentStatus.FAILED_OCR)
            // Clean up converted image if it was created from PDF and OCR failed
            if (isPdfConverted && fileToProcess.exists()) {
                fileToProcess.delete()
            }
            throw RuntimeException("OCR service call failed: ${e.message}", e)
        }


        val storedFilePath = uniqueFilename // Path relative to uploadDir for DB

        val result = when (fastApiResponse.documentType) {
            "Aadhar" -> {
                if (fastApiResponse.ocr.fields is Map<*, *>) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val fieldsMap = fastApiResponse.ocr.fields as Map<String, Any?>
                        val aadhaarFields = objectMapper.convertValue(fieldsMap, AadhaarOcrFields::class.java)

                        val dobStr = aadhaarFields.dateOfBirthYear ?: throw IllegalArgumentException("Date of birth missing for Aadhaar")
                        val dob = parseAadhaarDate(dobStr)

                        val pancardData = PancardData(
                            aadhaarNumber = aadhaarFields.aadharNumber?.replace(" ", "") ?: throw IllegalArgumentException("Aadhaar number missing"),
                            name = aadhaarFields.name ?: throw IllegalArgumentException("Name missing for Aadhaar"),
                            dob = dob,
                            gender = aadhaarFields.gender ?: throw IllegalArgumentException("Gender missing for Aadhaar"),
                            maskedImagePath = storedFilePath,
                            uploadedBy = username,
                            uploadedAt = LocalDateTime.now()
                        )
                        val saved = panDataRepository.save(pancardData)
                        logDocument(username, uniqueFilename, DocumentType.AADHAAR, DocumentStatus.UPLOADED)
                        logger.info("Aadhaar data saved for user $username: ${saved.id}")
                        saved
                    } catch (e: Exception) {
                        logger.error("Error processing Aadhaar data: ${e.message} for fields: ${fastApiResponse.ocr.fields}", e)
                        logDocument(username, uniqueFilename, DocumentType.AADHAAR, DocumentStatus.REJECTED, "Error: ${e.message}")
                        throw RuntimeException("Invalid Aadhaar data from OCR: ${e.message}", e)
                    }
                } else {
                    logDocument(username, uniqueFilename, DocumentType.AADHAAR, DocumentStatus.FAILED_OCR, "OCR fields not a map")
                    throw RuntimeException("OCR fields for Aadhaar are not in expected format.")
                }
            }
            "Driver's License" -> {
                if (fastApiResponse.ocr.fields is Map<*, *>) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val fieldsMap = fastApiResponse.ocr.fields as Map<String, Any?>
                        val dlFields = objectMapper.convertValue(fieldsMap, DrivingLicenseOcrFields::class.java)

                        val expiryDate = dlFields.expiryDate?.let { LocalDate.parse(it, dateFormatter) }
                            ?: throw IllegalArgumentException("Expiry date missing for Driving License")

                        val passportData = PassportData(
                            licenseNumber = dlFields.licenseNumber ?: throw IllegalArgumentException("License number missing"),
                            name = dlFields.name ?: throw IllegalArgumentException("Name missing for Driving License"),
                            expiryDate = expiryDate,
                            imagePath = storedFilePath,
                            uploadedBy = username,
                            uploadedAt = LocalDateTime.now()
                        )
                        val saved = passportDataRepository.save(passportData)
                        logDocument(username, uniqueFilename, DocumentType.DRIVING_LICENSE, DocumentStatus.UPLOADED)
                        logger.info("Driving License data saved for user $username: ${saved.id}")
                        saved
                    } catch (e: Exception) {
                        logger.error("Error processing Driving License data: ${e.message} for fields: ${fastApiResponse.ocr.fields}", e)
                        logDocument(username, uniqueFilename, DocumentType.DRIVING_LICENSE, DocumentStatus.REJECTED, "Error: ${e.message}")
                        throw RuntimeException("Invalid Driving License data from OCR: ${e.message}", e)
                    }
                } else {
                    logDocument(username, uniqueFilename, DocumentType.DRIVING_LICENSE, DocumentStatus.FAILED_OCR, "OCR fields not a map")
                    throw RuntimeException("OCR fields for Driving License are not in expected format.")
                }
            }
            "Unknown" -> {
                val message = if (fastApiResponse.ocr.fields is String) fastApiResponse.ocr.fields as String else "Unknown document type with non-string fields."
                logger.warn("Unknown document type received from FastAPI: $message for file $uniqueFilename by user $username")
                logDocument(username, uniqueFilename, DocumentType.UNKNOWN, DocumentStatus.REJECTED, message)
                mapOf("message" to "Unknown document type processed", "details" to message)
            }
            else -> {
                logger.warn("Unrecognized document type from FastAPI: ${fastApiResponse.documentType} for file $uniqueFilename by user $username")
                logDocument(username, uniqueFilename, DocumentType.UNKNOWN, DocumentStatus.FAILED_OCR, "Unrecognized type: ${fastApiResponse.documentType}")
                mapOf("message" to "Unrecognized document type: ${fastApiResponse.documentType}")
            }
        }

        // Clean up the converted image file if it was created from PDF
        if (isPdfConverted && fileToProcess.exists() && fileToProcess.path != originalStoredFile.path) {
            val deleted = fileToProcess.delete()
            if(deleted) logger.info("Cleaned up converted image: ${fileToProcess.name}")
            else logger.warn("Failed to clean up converted image: ${fileToProcess.name}")
        }
        return result
    }

    private fun parseAadhaarDate(dateStr: String): LocalDate {
        return try {
            LocalDate.parse(dateStr, dateFormatter) // "dd/MM/yyyy"
        } catch (e: DateTimeParseException) {
            try {
                // If it's just a year, e.g., "2004", we can't create a full LocalDate.
                // The table expects a full date. The example "27/09/2004" for "date_of_birth-year" suggests it should be full date.
                // If it were truly just year, this would need adjustment (e.g., store year as int or make day/month optional).
                // For now, strongly assume "dd/MM/yyyy" for "date_of_birth-year" based on example value.
                // If it can ALSO be just "yyyy", then this logic needs to be more robust.
                // As per example: "date_of_birth-year": "27/09/2004", this should parse with dateFormatter
                throw IllegalArgumentException("Aadhaar date format '$dateStr' is not 'dd/MM/yyyy'.")
            } catch (e2: DateTimeParseException) {
                throw IllegalArgumentException("Invalid date format for Aadhaar DOB: $dateStr. Expected dd/MM/yyyy.")
            }
        }
    }


    private fun logDocument(username: String, fileName: String, docType: DocumentType, status: DocumentStatus, details: String? = null) {
        val finalFileName = Paths.get(fileName).fileName.toString() // Get just the file name part
        var logMessage = "Document: $finalFileName, Type: $docType, Status: $status, Uploader: $username"
        if(details != null) {
            logMessage += ", Details: $details"
        }
        logger.info(logMessage)

        val documentLog = DocumentLog(
            uploader = username,
            fileName = finalFileName, // Store only the filename part
            docType = docType,
            status = status,
            createdAt = LocalDateTime.now()
        )
        documentLogRepository.save(documentLog)
    }
}