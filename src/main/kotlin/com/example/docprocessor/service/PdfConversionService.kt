package com.example.docprocessor.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader

@Service
class PdfConversionService {

    fun convertPdfToImage(pdfFile: File, outputDir: File): File {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        try {
            Loader.loadPDF(pdfFile).use { document ->
                val pdfRenderer = PDFRenderer(document)
                // Convert the first page to an image
                val bim: BufferedImage = pdfRenderer.renderImageWithDPI(0, 300f, ImageType.RGB)
                val outputImageFile = File(outputDir, "${pdfFile.nameWithoutExtension}_page1.png")
                ImageIO.write(bim, "PNG", outputImageFile)
                return outputImageFile
            }
        } catch (e: IOException) {
            throw RuntimeException("Error converting PDF to image: ${e.message}", e)
        }
    }
}