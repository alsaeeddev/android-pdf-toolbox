package com.example.feature_tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.PdfDocumentRepository
import com.example.feature_tools.domain.CompressQuality
import com.example.feature_tools.domain.CompressResult
import com.example.feature_tools.domain.ImageExportFormat
import com.example.feature_tools.domain.ImageExportResult
import com.example.feature_tools.domain.TextExtractResult
import com.example.feature_tools.domain.PdfToolsEngine
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ToolOperationState {
    data object Idle : ToolOperationState()
    data class Processing(val progressMessage: String) : ToolOperationState()
    data class Success(val title: String, val message: String, val resultUri: Uri) : ToolOperationState()
    data class CompressSuccess(val result: CompressResult) : ToolOperationState()
    data class ImagesSuccess(val result: ImageExportResult) : ToolOperationState()
    data class TextSuccess(val result: TextExtractResult) : ToolOperationState()
    data class ProtectSuccess(val outputFile: File, val outputUri: Uri) : ToolOperationState()
    data class Error(val message: String) : ToolOperationState()
}

class ToolsViewModel(
    private val context: Context,
    private val pdfRepository: PdfDocumentRepository
) : ViewModel() {

    private val _operationState = MutableStateFlow<ToolOperationState>(ToolOperationState.Idle)
    val operationState: StateFlow<ToolOperationState> = _operationState.asStateFlow()

    fun resetState() {
        _operationState.value = ToolOperationState.Idle
    }

    fun mergeDocuments(uris: List<Uri>, title: String = "Merged_Document") {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Merging ${uris.size} PDF files...")
            val result = PdfToolsEngine.mergePdfs(context, uris, title)
            if (result.isSuccess) {
                val (_, outputUri) = result.getOrThrow()
                pdfRepository.openPdf(outputUri)
                _operationState.value = ToolOperationState.Success(
                    title = "Merge Complete",
                    message = "Successfully merged ${uris.size} documents into a single PDF.",
                    resultUri = outputUri
                )
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to merge PDF files."
                )
            }
        }
    }

    fun splitDocument(uri: Uri, fromPage: Int, toPage: Int) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Extracting pages $fromPage to $toPage...")
            val result = PdfToolsEngine.splitPdf(context, uri, fromPage, toPage)
            if (result.isSuccess) {
                val (_, outputUri) = result.getOrThrow()
                pdfRepository.openPdf(outputUri)
                _operationState.value = ToolOperationState.Success(
                    title = "Split Complete",
                    message = "Extracted pages $fromPage to $toPage into a new PDF.",
                    resultUri = outputUri
                )
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to split PDF."
                )
            }
        }
    }

    fun compressDocument(uri: Uri, quality: CompressQuality) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Compressing PDF with ${quality.label}...")
            val result = PdfToolsEngine.compressPdf(context, uri, quality)
            if (result.isSuccess) {
                val compressResult = result.getOrThrow()
                pdfRepository.openPdf(compressResult.outputUri)
                _operationState.value = ToolOperationState.CompressSuccess(compressResult)
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to compress PDF."
                )
            }
        }
    }

    fun convertImagesToPdf(imageUris: List<Uri>, title: String = "Images_Document") {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Converting ${imageUris.size} images to PDF...")
            val result = PdfToolsEngine.imagesToPdf(context, imageUris, title)
            if (result.isSuccess) {
                val (_, outputUri) = result.getOrThrow()
                pdfRepository.openPdf(outputUri)
                _operationState.value = ToolOperationState.Success(
                    title = "Images Converted",
                    message = "Created PDF document from ${imageUris.size} images.",
                    resultUri = outputUri
                )
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to convert images."
                )
            }
        }
    }

    fun extractText(uri: Uri) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Reading text...")
            val result = PdfToolsEngine.extractText(context, uri) { current, total ->
                _operationState.value = ToolOperationState.Processing("Extracting text from page $current of $total...")
            }
            if (result.isSuccess) {
                _operationState.value = ToolOperationState.TextSuccess(result.getOrThrow())
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to extract text."
                )
            }
        }
    }

    fun protectDocument(uri: Uri, password: String) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Encrypting PDF with AES-256...")
            val result = PdfToolsEngine.protectPdf(context, uri, password)
            if (result.isSuccess) {
                // Encrypted PDF in-app viewer (PdfRenderer) me nahi khul sakti, is liye openPdf() nahi bulate.
                val (file, outputUri) = result.getOrThrow()
                _operationState.value = ToolOperationState.ProtectSuccess(file, outputUri)
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to protect PDF."
                )
            }
        }
    }

    fun exportPdfToImages(uri: Uri, format: ImageExportFormat) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Preparing pages...")
            val result = PdfToolsEngine.pdfToImages(context, uri, format) { current, total ->
                _operationState.value = ToolOperationState.Processing("Exporting page $current of $total...")
            }
            if (result.isSuccess) {
                _operationState.value = ToolOperationState.ImagesSuccess(result.getOrThrow())
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to export PDF pages as images."
                )
            }
        }
    }

    fun applyWatermark(uri: Uri, watermarkText: String) {
        viewModelScope.launch {
            _operationState.value = ToolOperationState.Processing("Adding watermark to document...")
            val result = PdfToolsEngine.addWatermark(context, uri, watermarkText)
            if (result.isSuccess) {
                val (_, outputUri) = result.getOrThrow()
                pdfRepository.openPdf(outputUri)
                _operationState.value = ToolOperationState.Success(
                    title = "Watermark Applied",
                    message = "Added '$watermarkText' across all document pages.",
                    resultUri = outputUri
                )
            } else {
                _operationState.value = ToolOperationState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to apply watermark."
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            context: Context,
            pdfRepository: PdfDocumentRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ToolsViewModel(context, pdfRepository) as T
            }
        }
    }
}
