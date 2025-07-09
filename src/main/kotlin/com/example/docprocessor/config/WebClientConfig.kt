package com.example.docprocessor.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    @Value("\${ocr.service.url}") private val ocrServiceUrl: String
) {

    @Bean
    fun ocrWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(ocrServiceUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}