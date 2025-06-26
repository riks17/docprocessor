package com.example.docprocessor.service

import jakarta.annotation.PostConstruct // <--- Add this import
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class FileStorageService {

    @Value("\${file.upload-dir}")
    private lateinit var uploadDir: String

    // Keep rootLocation lazy, but its initialization will be triggered by createUploadDirectory
    private val rootLocation: Path by lazy { Paths.get(uploadDir).toAbsolutePath().normalize() }

    @PostConstruct // This method will be called after dependency injection is done
    fun initService() {
        try {
            Files.createDirectories(rootLocation)
        } catch (ex: Exception) {
            throw RuntimeException("Could not create the directory specified by 'file.upload-dir': $uploadDir", ex)
        }
    }

    fun storeFile(file: MultipartFile): Pair<File, String> {
        val originalFilename = file.originalFilename ?: "unknownfile"
        val sanitizedFilename = originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val uniqueFilename = UUID.randomUUID().toString() + "_" + sanitizedFilename

        try {
            if (uniqueFilename.contains("..")) {
                throw RuntimeException("Cannot store file with relative path outside current directory $uniqueFilename")
            }
            // Ensure rootLocation is initialized before resolving
            val targetLocation = this.rootLocation.resolve(uniqueFilename)
            Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)
            return Pair(targetLocation.toFile(), uniqueFilename)
        } catch (ex: IOException) {
            throw RuntimeException("Failed to store file $uniqueFilename", ex)
        }
    }

    fun storeTempFile(file: MultipartFile, prefix: String, suffix: String): File {
        try {
            // Ensure rootLocation is initialized
            val tempFile = Files.createTempFile(rootLocation, prefix, suffix).toFile()
            file.transferTo(tempFile)
            return tempFile
        } catch (e: IOException) {
            throw RuntimeException("Could not store temp file", e)
        }
    }
}