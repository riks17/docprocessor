package com.example.docprocessor.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    // This reads the URL from your application.properties file
    @Value("\${ocr.service.url}") private val ocrServiceUrl: String
) {

    @Bean
    fun ocrWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(ocrServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}