package com.example.docprocessor.service

import com.example.docprocessor.dto.DocumentProcessingResult
import com.example.docprocessor.dto.OcrResponse
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
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    init {
        try {
            Files.createDirectories(fileStorageLocation)
            Files.createDirectories(tempFileLocation)
        } catch (ex: Exception) {
            throw RuntimeException("Could not create storage/temp directories.", ex)
        }
    }

    @Transactional
    suspend fun processUploadedDocument(multipartFile: MultipartFile, username: String): DocumentProcessingResult {
        var fileToProcess: File? = null
        try {
            // 1. Prepare file for OCR (convert PDF to image if necessary) in a temp location
            fileToProcess = prepareFileForOcr(multipartFile)

            // 2. Call Python OCR Microservice asynchronously
            val ocrResponse = callOcrMicroservice(fileToProcess)

            if (ocrResponse.status != "SUCCESS") {
                logDocument(DocumentType.UNKNOWN, multipartFile.originalFilename!!, username, DocumentStatus.FAILED_OCR)
                throw IllegalStateException("OCR processing failed: ${ocrResponse.errorMessage}")
            }

            val docType = DocumentType.valueOf(ocrResponse.documentType)

            // 3. Store the original file permanently in the final storage location
            val savedPath = storeOriginalFile(multipartFile, docType)

            // 4. Save the extracted data to the correct database table
            val savedEntityId = saveData(ocrResponse, savedPath.toString(), username)

            // 5. Log the successful transaction
            logDocument(docType, multipartFile.originalFilename!!, username, DocumentStatus.VERIFIED)

            return DocumentProcessingResult(
                documentId = savedEntityId,
                documentType = ocrResponse.documentType,
                message = "Document processed and saved successfully."
            )
        } finally {
            // 6. Clean up temporary files
            fileToProcess?.delete()
        }
    }

    private suspend fun callOcrMicroservice(file: File): OcrResponse {
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", file.readBytes(), MediaType.APPLICATION_OCTET_STREAM).filename(file.name)

        logger.info("Sending file '${file.name}' to OCR microservice at /ocr/process")
        return ocrWebClient.post()
            .uri("/ocr/process") // The endpoint on your Python service
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .awaitBody<OcrResponse>()
    }

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
        pdfFile.delete() // Delete the temporary PDF, keep the PNG
        pngFile
    }

    private fun saveData(ocrResponse: OcrResponse, imagePath: String, username: String): Long {
        val data = ocrResponse.data
        val docType = DocumentType.valueOf(ocrResponse.documentType)

        return when (docType) {
            DocumentType.PAN_CARD -> panDataRepository.save(
                PanData(
                    name = data["name"] ?: throw IllegalArgumentException("Name is missing for PAN"),
                    dob = LocalDate.parse(data["dob"], dateFormatter),
                    panNumber = data["pan_number"] ?: throw IllegalArgumentException("PAN number is missing"),
                    imagePath = imagePath,
                    uploadedBy = username
                )
            ).id!!
            DocumentType.VOTER_ID -> voterIdDataRepository.save(
                VoterIdData(
                    name = data["name"] ?: throw IllegalArgumentException("Name is missing for Voter ID"),
                    dob = LocalDate.parse(data["dob"], dateFormatter),
                    voterIdNumber = data["voter_id_number"] ?: throw IllegalArgumentException("Voter ID number is missing"),
                    imagePath = imagePath,
                    uploadedBy = username
                )
            ).id!!
            DocumentType.PASSPORT -> passportDataRepository.save(
                PassportData(
                    name = data["name"] ?: throw IllegalArgumentException("Name is missing for Passport"),
                    dob = LocalDate.parse(data["dob"], dateFormatter),
                    passportNumber = data["passport_number"] ?: throw IllegalArgumentException("Passport number is missing"),
                    imagePath = imagePath,
                    uploadedBy = username
                )
            ).id!!
            DocumentType.UNKNOWN -> throw IllegalArgumentException("Cannot save data for UNKNOWN document type.")
        }
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

    // --- Methods to support the controller's GET endpoints ---
    fun getPanDataById(id: Long): PanData? = panDataRepository.findById(id).orElse(null)
    fun getVoterIdDataById(id: Long): VoterIdData? = voterIdDataRepository.findById(id).orElse(null)
    fun getPassportDataById(id: Long): PassportData? = passportDataRepository.findById(id).orElse(null)
}