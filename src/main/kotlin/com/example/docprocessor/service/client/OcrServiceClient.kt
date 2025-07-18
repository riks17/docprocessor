package com.example.docprocessor.service.client

import com.example.docprocessor.dto.PythonOcrResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile


@FeignClient(name = "ocr-service", url = "\${ocr.service.url}")
interface OcrServiceClient {

    @PostMapping(value = ["/ocr/process/"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun processDocument(
        @RequestPart("files") file: MultipartFile
    ): PythonOcrResponse
}