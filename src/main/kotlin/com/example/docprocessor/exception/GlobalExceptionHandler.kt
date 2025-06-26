package com.example.docprocessor.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException

data class ErrorDetails(val timestamp: Long, val message: String?, val details: String)

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxSizeException(exc: MaxUploadSizeExceededException, request: WebRequest): ResponseEntity<ErrorDetails> {
        logger.warn("File too large: ${exc.message}")
        val errorDetails = ErrorDetails(System.currentTimeMillis(), "File too large!", request.getDescription(false))
        return ResponseEntity(errorDetails, HttpStatus.PAYLOAD_TOO_LARGE)
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleGenericRuntimeException(ex: RuntimeException, request: WebRequest): ResponseEntity<ErrorDetails> {
        logger.error("Runtime exception: ${ex.message}", ex) // Log the full stack trace
        val errorDetails = ErrorDetails(System.currentTimeMillis(), ex.message, request.getDescription(false))
        return ResponseEntity(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<ErrorDetails> {
        logger.warn("Illegal argument: ${ex.message}")
        val errorDetails = ErrorDetails(System.currentTimeMillis(), ex.message, request.getDescription(false))
        return ResponseEntity(errorDetails, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun globalExceptionHandler(ex: Exception, request: WebRequest): ResponseEntity<ErrorDetails> {
        logger.error("Unhandled exception: ${ex.message}", ex) // Log the full stack trace
        val errorDetails = ErrorDetails(System.currentTimeMillis(), "An unexpected error occurred: ${ex.message}", request.getDescription(false))
        return ResponseEntity(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}