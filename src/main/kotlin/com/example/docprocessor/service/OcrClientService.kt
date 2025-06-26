package com.example.docprocessor.service

import com.example.docprocessor.dto.FastApiResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.io.File

@Service
class OcrClientService(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${fastapi.url}") private val fastapiUrl: String
) {
    private val logger = LoggerFactory.getLogger(OcrClientService::class.java)

    fun callFastApiProcessDocument(imageFile: File): Mono<FastApiResponse> {
        logger.info("Calling FastAPI at $fastapiUrl with image: ${imageFile.name}")

        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", FileSystemResource(imageFile))
            .contentType(MediaType.IMAGE_PNG) // Adjust if necessary (e.g. IMAGE_JPEG)

        return webClientBuilder.build()
            .post()
            .uri(fastapiUrl)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .onStatus({ status -> status.isError }) { clientResponse ->
                clientResponse.bodyToMono(String::class.java)
                    .flatMap { errorBody ->
                        logger.error("FastAPI error: ${clientResponse.statusCode()} - $errorBody")
                        Mono.error(RuntimeException("FastAPI call failed with status ${clientResponse.statusCode()} and body: $errorBody"))
                    }
            }
            .bodyToMono(FastApiResponse::class.java)
            .doOnSuccess { response -> logger.info("FastAPI response received: $response") }
            .doOnError { error -> logger.error("Error calling FastAPI: ${error.message}", error) }
    }
}