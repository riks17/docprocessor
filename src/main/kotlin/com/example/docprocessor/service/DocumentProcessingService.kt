package com.example.docprocessor.service

import com.example.docprocessor.dto.PythonOcrResponse
import com.example.docprocessor.model.*
import com.example.docprocessor.repository.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO

@Service
class DocumentProcessingService(
    private val ocrWebClient: WebClient,
    private val panDataRepository: PanDataRepository,
    private val voterIdDataRepository: VoterIdDataRepository,
    private val passportDataRepository: PassportDataRepository,
    private val documentLogRepository: DocumentLogRepository
) {
    private val logger = LoggerFactory.getLogger(DocumentProcessingService::class.java)
    private val fileStorageLocation: Path = Paths.get("./storage").toAbsolutePath().normalize()
    private val tempFileLocation: Path = Paths.get("./temp").toAbsolutePath().normalize()
    private val timestampDirFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    init {
        try {
            Files.createDirectories(fileStorageLocation)
            Files.createDirectories(tempFileLocation)
        } catch (ex: Exception) {
            throw RuntimeException("Could not create storage/temp directories.", ex)
        }
    }

    // --- The main processUploadedDocument method is unchanged ---
    @Transactional
    fun processUploadedDocument(multipartFile: MultipartFile, username: String): Any {
        var tempStagedFile: File? = null
        var convertedImageFile: File? = null
        val processingTimestamp = LocalDateTime.now()

        try {
            val (stagedFile, uniqueFilename) = storeFileToTemp(multipartFile)
            tempStagedFile = stagedFile
            var fileToProcess = tempStagedFile

            if (isPdf(multipartFile, tempStagedFile)) {
                logger.info("PDF file detected: ${tempStagedFile.name}. Converting to image.")
                convertedImageFile = convertPdfToImage(tempStagedFile)
                fileToProcess = convertedImageFile
            }

            val pythonResponse = callOcrService(fileToProcess)
            val ocrResult = pythonResponse.results.firstOrNull()
                ?: throw IllegalStateException("OCR service returned an empty result list.")

            if (ocrResult.ocrResults == null || ocrResult.message != null || ocrResult.error != null) {
                val errorMessage = ocrResult.message ?: ocrResult.error ?: "Unknown error from OCR service"
                throw IllegalStateException("OCR processing failed: $errorMessage")
            }

            val fieldsMap = ocrResult.ocrResults
            val docType = mapPythonDocTypeToEnum(ocrResult.documentType)

            if (docType == DocumentType.UNKNOWN) {
                throw IllegalArgumentException("Unsupported document type received: ${ocrResult.documentType}")
            }

            val finalRelativePath = moveFileToFinal(tempStagedFile, docType, processingTimestamp, uniqueFilename)
            tempStagedFile = null

            val storedFilePathForDb = finalRelativePath.toString().replace(File.separatorChar, '/')

            val result = saveData(docType, fieldsMap, storedFilePathForDb, username, processingTimestamp)
            logDocument(username, uniqueFilename, docType, DocumentStatus.VERIFIED)
            return result

        } catch (e: Exception) {
            logger.error("Document processing failed for user '$username': ${e.message}", e)
            logDocument(username, multipartFile.originalFilename ?: "unknown", DocumentType.UNKNOWN, DocumentStatus.REJECTED, e.message)
            throw e
        } finally {
            convertedImageFile?.delete()
            tempStagedFile?.delete()
            logger.info("Cleanup of temporary files complete for user '$username'.")
        }
    }


    // --- THE `saveData` METHOD IS NOW UPDATED WITH THE NEW LOGIC ---
    private fun saveData(docType: DocumentType, fields: Map<String, String>, path: String, user: String, timestamp: LocalDateTime): Any {
        return when (docType) {
            DocumentType.PAN_CARD -> {
                // Only the PAN number is required. The rest are optional.
                val panNumber = fields["pan"] ?: throw IllegalArgumentException("PAN number is required and was not extracted.")
                panDataRepository.save(PanData(
                    name = fields["name"],
                    fathersName = fields["father"],
                    dob = fields["dob"]?.let { parseDate(it, "PAN") },
                    panNumber = panNumber,
                    imagePath = path,
                    uploadedBy = user,
                    uploadedAt = timestamp
                ))
            }
            DocumentType.VOTER_ID -> {
                // Only the Voter ID number is required. The rest are optional.
                val voterIdNumber = fields["voter_id"] ?: throw IllegalArgumentException("Voter ID number is required and was not extracted.")
                voterIdDataRepository.save(VoterIdData(
                    name = fields["name"],
                    gender = fields["gender"],
                    dob = fields["date"]?.let { parseDate(it, "Voter ID") },
                    voterIdNumber = voterIdNumber,
                    imagePath = path,
                    uploadedBy = user,
                    uploadedAt = timestamp
                ))
            }
            DocumentType.PASSPORT -> {
                // Only the Passport number is required. The rest are optional.
                val passportNumber = fields["passport_number"] ?: throw IllegalArgumentException("Passport number is required and was not extracted.")
                passportDataRepository.save(PassportData(
                    name = fields["name"],
                    gender = fields["gender"],
                    dob = fields["dob"]?.let { parseDate(it, "Passport DOB") },
                    passportNumber = passportNumber,
                    expiryDate = fields["expiry"]?.let { parseDate(it, "Passport Expiry Date") },
                    imagePath = path,
                    uploadedBy = user,
                    uploadedAt = timestamp
                ))
            }
            DocumentType.UNKNOWN -> throw IllegalStateException("Should not attempt to save UNKNOWN document type.")
        }
    }

    // --- All other private helper methods remain the same ---

    fun getPanDataById(id: Long): PanData? = panDataRepository.findById(id).orElse(null)
    fun getVoterIdDataById(id: Long): VoterIdData? = voterIdDataRepository.findById(id).orElse(null)
    fun getPassportDataById(id: Long): PassportData? = passportDataRepository.findById(id).orElse(null)

    private fun storeFileToTemp(file: MultipartFile): Pair<File, String> {
        val uniqueFilename = "${System.currentTimeMillis()}_${UUID.randomUUID()}_${file.originalFilename}"
        val tempFilePath = tempFileLocation.resolve(uniqueFilename)
        file.inputStream.use { input -> Files.copy(input, tempFilePath, StandardCopyOption.REPLACE_EXISTING) }
        return Pair(tempFilePath.toFile(), uniqueFilename)
    }

    private fun moveFileToFinal(tempFile: File, docType: DocumentType, timestamp: LocalDateTime, uniqueFilename: String): Path {
        val dateDir = timestamp.format(timestampDirFormatter)
        val finalDir = fileStorageLocation.resolve(docType.name).resolve(dateDir)
        Files.createDirectories(finalDir)
        val finalPath = finalDir.resolve(uniqueFilename)
        return Files.move(tempFile.toPath(), finalPath, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun convertPdfToImage(pdfFile: File): File {
        val pngFile = Files.createTempFile(tempFileLocation, "converted-", ".png").toFile()
        Loader.loadPDF(pdfFile).use { document ->
            val renderer = PDFRenderer(document)
            val image: BufferedImage = renderer.renderImageWithDPI(0, 300f)
            ImageIO.write(image, "PNG", pngFile)
        }
        return pngFile
    }

    private fun callOcrService(file: File): PythonOcrResponse {
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("files", file.readBytes(), MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition", "form-data; name=\"files\"; filename=\"${file.name}\"")
        logger.info("Sending file '${file.name}' to OCR microservice at /ocr/process/")
        try {
            return ocrWebClient.post()
                .uri("/ocr/process/")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(PythonOcrResponse::class.java)
                .block() ?: throw IllegalStateException("OCR service returned an empty response body.")
        } catch (e: WebClientResponseException) {
            logger.error("Error from OCR service: ${e.statusCode} ${e.getResponseBodyAsString()}", e)
            throw IllegalStateException("Failed to call OCR service: ${e.message}")
        }
    }

    private fun isPdf(multipartFile: MultipartFile, file: File): Boolean =
        "application/pdf".equals(multipartFile.contentType, ignoreCase = true) || "pdf".equals(file.extension, ignoreCase = true)

    private fun parseDate(dateStr: String, docTypeName: String): LocalDate {
        // Removed the null check as we now use ?.let to call this function safely
        try {
            return LocalDate.parse(dateStr, dateFormatter)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format '$dateStr' for $docTypeName. Expected dd/MM/yyyy.")
        }
    }

    private fun logDocument(username: String, fileName: String, docType: DocumentType, status: DocumentStatus, details: String? = null) {
        documentLogRepository.save(DocumentLog(
            uploader = username,
            fileName = Paths.get(fileName).fileName.toString(),
            docType = docType,
            status = status,
            createdAt = LocalDateTime.now()
        ))
    }

    private fun mapPythonDocTypeToEnum(pythonDocType: String): DocumentType =
        when (pythonDocType.lowercase()) {
            "pan" -> DocumentType.PAN_CARD
            "passport" -> DocumentType.PASSPORT
            "voterid_new", "voterid_old" -> DocumentType.VOTER_ID
            else -> DocumentType.UNKNOWN
        }
}