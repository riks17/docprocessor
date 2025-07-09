package com.example.docprocessor.service

import com.example.docprocessor.dto.DocumentProcessingResult
import com.example.docprocessor.dto.PythonOcrResponse
import com.example.docprocessor.model.*
import com.example.docprocessor.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.IO
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
import org.springframework.web.reactive.function.client.awaitBody
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    init {
        try {
            Files.createDirectories(fileStorageLocation)
            Files.createDirectories(tempFileLocation)
        } catch (ex: Exception) {
            throw RuntimeException("Could not create storage/temp directories.", ex)
        }
    }

    @Transactional
    suspend fun processUploadedDocument(multipartFile: MultipartFile, username: String): Any {
        var fileToProcess: File? = null
        try {
            fileToProcess = prepareFileForOcr(multipartFile)
            val pythonResponse = callOcrMicroservice(fileToProcess)

            val ocrResult = pythonResponse.results.firstOrNull()
                ?: throw IllegalStateException("OCR service returned an empty result list.")

            if (ocrResult.ocrResults == null || ocrResult.message != null || ocrResult.error != null) {
                val errorMessage = ocrResult.message ?: ocrResult.error ?: "Unknown error from OCR service"
                logDocument(DocumentType.UNKNOWN, multipartFile.originalFilename!!, username, DocumentStatus.FAILED_OCR)
                throw IllegalStateException("OCR processing failed: $errorMessage")
            }

            val docType = mapPythonDocTypeToEnum(ocrResult.documentType)
            if (docType == DocumentType.UNKNOWN) {
                logDocument(docType, multipartFile.originalFilename!!, username, DocumentStatus.REJECTED)
                throw IllegalArgumentException("Unsupported or unknown document type received: ${ocrResult.documentType}")
            }

            val savedPath = storeOriginalFile(multipartFile, docType)
            // Save data robustly
            saveData(docType, ocrResult.ocrResults, savedPath.toString(), username)
            logDocument(docType, multipartFile.originalFilename!!, username, DocumentStatus.VERIFIED)

            // Return the raw OCR results as the response
            return ocrResult.ocrResults
        } finally {
            fileToProcess?.delete()
        }
    }

    private suspend fun callOcrMicroservice(file: File): PythonOcrResponse {
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("files", file.readBytes(), MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition", "form-data; name=\"files\"; filename=\"${file.name}\"")

        logger.info("Sending file '${file.name}' to OCR microservice at /ocr/process/")
        return ocrWebClient.post()
            .uri("/ocr/process/")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .awaitBody<PythonOcrResponse>()
    }

    // --- ROBUST saveData FUNCTION ---
    // This version will not crash. If a field is missing or a date is invalid, it logs a warning
    // and proceeds to save the entity with a default or null value.
    private fun saveData(docType: DocumentType, data: Map<String, String>, imagePath: String, username: String) {
        when (docType) {
            DocumentType.PAN_CARD -> {
                val dob = try {
                    LocalDate.parse(data["dob"], dateFormatter)
                } catch (e: Exception) {
                    logger.warn("Could not parse PAN DOB '${data["dob"]}'. Defaulting to epoch.")
                    LocalDate.EPOCH // Use a default value
                }
                panDataRepository.save(
                    PanData(
                        name = data["name"] ?: "N/A",
                        dob = dob,
                        panNumber = data["pan"] ?: "N/A",
                        imagePath = imagePath,
                        uploadedBy = username
                    )
                )
            }
            DocumentType.VOTER_ID -> {
                val dob = try {
                    LocalDate.parse(data["date"], dateFormatter)
                } catch (e: Exception) {
                    logger.warn("Could not parse Voter ID DOB '${data["date"]}'. Defaulting to epoch.")
                    LocalDate.EPOCH
                }
                voterIdDataRepository.save(
                    VoterIdData(
                        name = data["name"] ?: "N/A",
                        dob = dob,
                        voterIdNumber = data["voter_id"] ?: "N/A",
                        imagePath = imagePath,
                        uploadedBy = username
                    )
                )
            }
            DocumentType.PASSPORT -> {
                val dob = try {
                    LocalDate.parse(data["dob"], dateFormatter)
                } catch (e: Exception) {
                    logger.warn("Could not parse Passport DOB '${data["dob"]}'. Defaulting to epoch.")
                    LocalDate.EPOCH
                }
                passportDataRepository.save(
                    PassportData(
                        name = data["name"] ?: "N/A",
                        dob = dob,
                        passportNumber = data["passport_number"] ?: "N/A",
                        imagePath = imagePath,
                        uploadedBy = username
                    )
                )
            }
            DocumentType.UNKNOWN -> {
                logger.warn("Attempted to save data for an UNKNOWN document type. Skipping.")
            }
        }
    }


    // --- Other helper functions remain the same ---

    private suspend fun prepareFileForOcr(multipartFile: MultipartFile): File {
        val extension = multipartFile.originalFilename?.substringAfterLast('.', "")
        val tempFile = withContext(Dispatchers.IO) {
            Files.createTempFile(tempFileLocation, "upload-", ".$extension").toFile()
        }
        multipartFile.inputStream.use { input -> Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }

        if (multipartFile.contentType == "application/pdf" || extension.equals("pdf", true)) {
            logger.info("PDF detected. Converting to PNG for OCR.")
            return convertPdfToImage(tempFile)
        }
        return tempFile
    }

    private suspend fun convertPdfToImage(pdfFile: File): File = withContext(Dispatchers.IO) {
        val pngFile = Files.createTempFile(tempFileLocation, "converted-", ".png").toFile()
        Loader.loadPDF(pdfFile).use { document ->
            val renderer = PDFRenderer(document)
            val image: BufferedImage = renderer.renderImageWithDPI(0, 300f)
            ImageIO.write(image, "PNG", pngFile)
        }
        pdfFile.delete()
        pngFile
    }

    private fun storeOriginalFile(file: MultipartFile, docType: DocumentType): Path {
        val docTypePath = fileStorageLocation.resolve(docType.name)
        Files.createDirectories(docTypePath)
        val uniqueFileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}_${file.originalFilename}"
        val targetLocation = docTypePath.resolve(uniqueFileName)
        file.inputStream.use { input -> Files.copy(input, targetLocation, StandardCopyOption.REPLACE_EXISTING) }
        return targetLocation
    }

    private fun logDocument(docType: DocumentType, fileName: String, uploader: String, status: DocumentStatus) {
        documentLogRepository.save(
            DocumentLog(
                docType = docType,
                fileName = Paths.get(fileName).fileName.toString(),
                uploader = uploader,
                status = status
            )
        )
    }

    private fun mapPythonDocTypeToEnum(pythonDocType: String): DocumentType {
        return when (pythonDocType.lowercase()) {
            "pan" -> DocumentType.PAN_CARD
            "passport" -> DocumentType.PASSPORT
            "voterid_new", "voterid_old" -> DocumentType.VOTER_ID
            else -> DocumentType.UNKNOWN
        }
    }

    fun getPanDataById(id: Long): PanData? = panDataRepository.findById(id).orElse(null)
    fun getVoterIdDataById(id: Long): VoterIdData? = voterIdDataRepository.findById(id).orElse(null)
    fun getPassportDataById(id: Long): PassportData? = passportDataRepository.findById(id).orElse(null)
}