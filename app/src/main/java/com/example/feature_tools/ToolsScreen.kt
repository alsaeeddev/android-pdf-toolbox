package com.example.feature_tools

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature_tools.domain.CompressQuality
import com.example.feature_tools.domain.ImageExportFormat
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandTeal

/** "Save as" picker: har androidx.activity version par chalta hai (CreateDocument ka naya constructor zaroori nahi). */
private class CreateFileDocument(private val mimeType: String) : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color
)

val pdfToolsList = listOf(
    ToolItem(
        id = "merge",
        title = "Merge PDFs",
        description = "Combine multiple PDF documents into one continuous file.",
        icon = Icons.Default.MergeType,
        iconTint = BrandIndigo
    ),
    ToolItem(
        id = "split",
        title = "Split PDF",
        description = "Extract pages or divide a PDF into separate files.",
        icon = Icons.Default.CallSplit,
        iconTint = BrandTeal
    ),
    ToolItem(
        id = "compress",
        title = "Compress PDF",
        description = "Reduce file size for faster email and messaging sharing.",
        icon = Icons.Default.Compress,
        iconTint = Color(0xFFF59E0B)
    ),
    ToolItem(
        id = "images_to_pdf",
        title = "Images to PDF",
        description = "Convert photos, scans, and gallery images into a PDF.",
        icon = Icons.Default.Collections,
        iconTint = Color(0xFFEC4899)
    ),
    ToolItem(
        id = "protect",
        title = "Protect with Password",
        description = "Encrypt your confidential documents with standard AES-256.",
        icon = Icons.Default.Lock,
        iconTint = Color(0xFF10B981)
    ),
    ToolItem(
        id = "pdf_to_images",
        title = "PDF to Images",
        description = "Export document pages as high-resolution PNG or JPG image files.",
        icon = Icons.Default.Image,
        iconTint = Color(0xFF8B5CF6)
    ),
    ToolItem(
        id = "watermark",
        title = "Add Watermark",
        description = "Stamp customized text or company notices onto all pages.",
        icon = Icons.Default.Security,
        iconTint = Color(0xFF06B6D4)
    ),
    ToolItem(
        id = "extract_text",
        title = "Extract Text",
        description = "Extract raw text from PDF document into a .txt file.",
        icon = Icons.Default.TextFields,
        iconTint = Color(0xFF64748B)
    )
)

@Composable
fun ToolsContent(
    viewModel: ToolsViewModel? = null,
    onOpenDocumentInViewer: (String) -> Unit = {},
    onOpenTool: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val operationState = viewModel?.operationState?.collectAsStateWithLifecycle()?.value ?: ToolOperationState.Idle

    // Modal input states
    var activeToolId by remember { mutableStateOf<String?>(null) }
    var selectedSinglePdfUri by remember { mutableStateOf<Uri?>(null) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showPdfToImagesDialog by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    // Pickers
    val pickSinglePdfForProtect = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSinglePdfUri = uri
            showProtectDialog = true
        }
    }

    fun copyFileTo(source: File, target: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    } != null
                } catch (_: Exception) {
                    false
                }
            }
            Toast.makeText(
                context,
                if (ok) "File saved." else "Could not save the file.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = CreateFileDocument("application/pdf")
    ) { target: Uri? ->
        val source = pendingSaveFile
        pendingSaveFile = null
        if (target != null && source != null) copyFileTo(source, target)
    }

    val saveTextLauncher = rememberLauncherForActivityResult(
        contract = CreateFileDocument("text/plain")
    ) { target: Uri? ->
        val source = pendingSaveFile
        pendingSaveFile = null
        if (target != null && source != null) copyFileTo(source, target)
    }

    val pickSinglePdfForText = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel?.extractText(uri)
    }

    val pickSinglePdfForImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSinglePdfUri = uri
            showPdfToImagesDialog = true
        }
    }

    val pickSinglePdfForSplit = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSinglePdfUri = uri
            showSplitDialog = true
        }
    }

    val pickSinglePdfForCompress = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSinglePdfUri = uri
            showCompressDialog = true
        }
    }

    val pickSinglePdfForWatermark = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSinglePdfUri = uri
            showWatermarkDialog = true
        }
    }

    val pickMultiplePdfsForMerge = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.size >= 2) {
            viewModel?.mergeDocuments(uris)
        }
    }

    val pickImagesToPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel?.convertImagesToPdf(uris)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("tools_grid")
    ) {
        items(pdfToolsList, key = { it.id }) { tool ->
            Card(
                onClick = {
                    when (tool.id) {
                        "merge" -> pickMultiplePdfsForMerge.launch(arrayOf("application/pdf"))
                        "split" -> pickSinglePdfForSplit.launch(arrayOf("application/pdf"))
                        "compress" -> pickSinglePdfForCompress.launch(arrayOf("application/pdf"))
                        "images_to_pdf" -> pickImagesToPdf.launch("image/*")
                        "extract_text" -> pickSinglePdfForText.launch(arrayOf("application/pdf"))
                        "protect" -> pickSinglePdfForProtect.launch(arrayOf("application/pdf"))
                        "pdf_to_images" -> pickSinglePdfForImages.launch(arrayOf("application/pdf"))
                        "watermark" -> pickSinglePdfForWatermark.launch(arrayOf("application/pdf"))
                        else -> onOpenTool(tool.id)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tool_card_${tool.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = tool.iconTint.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = tool.iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
        }
    }

    // Split Dialog
    if (showSplitDialog && selectedSinglePdfUri != null) {
        var fromPageText by remember { mutableStateOf("1") }
        var toPageText by remember { mutableStateOf("2") }

        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text("Split Page Range") },
            text = {
                Column {
                    Text("Select start and end pages to extract into a new PDF:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromPageText,
                            onValueChange = { fromPageText = it.filter { c -> c.isDigit() } },
                            label = { Text("From") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = toPageText,
                            onValueChange = { toPageText = it.filter { c -> c.isDigit() } },
                            label = { Text("To") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = fromPageText.toIntOrNull() ?: 1
                        val to = toPageText.toIntOrNull() ?: 2
                        showSplitDialog = false
                        viewModel?.splitDocument(selectedSinglePdfUri!!, from, to)
                    }
                ) {
                    Text("Split PDF", fontWeight = FontWeight.Bold, color = BrandIndigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Compress Dialog
    if (showCompressDialog && selectedSinglePdfUri != null) {
        var selectedQuality by remember { mutableStateOf(CompressQuality.MEDIUM) }

        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress Document") },
            text = {
                Column {
                    Text("Select desired compression strength:")
                    Spacer(modifier = Modifier.height(8.dp))
                    CompressQuality.entries.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedQuality = q }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == q,
                                onClick = { selectedQuality = q }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(q.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCompressDialog = false
                        viewModel?.compressDocument(selectedSinglePdfUri!!, selectedQuality)
                    }
                ) {
                    Text("Compress", fontWeight = FontWeight.Bold, color = BrandIndigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Protect Dialog
    if (showProtectDialog && selectedSinglePdfUri != null) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        val tooShort = password.isNotEmpty() && password.length < 4
        val mismatch = confirmPassword.isNotEmpty() && confirmPassword != password
        val canSubmit = password.length >= 4 && password == confirmPassword

        AlertDialog(
            onDismissRequest = { showProtectDialog = false },
            title = { Text("Protect with Password") },
            text = {
                Column {
                    Text("Set a password. The PDF will be encrypted with AES-256 and cannot be opened without it.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = tooShort,
                        supportingText = { if (tooShort) Text("At least 4 characters") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = { if (mismatch) Text("Passwords do not match") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "There is no way to recover the PDF if you forget this password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSubmit,
                    onClick = {
                        showProtectDialog = false
                        viewModel?.protectDocument(selectedSinglePdfUri!!, password)
                    }
                ) {
                    Text("Protect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectDialog = false }) { Text("Cancel") }
            }
        )
    }

    // PDF to Images Dialog
    if (showPdfToImagesDialog && selectedSinglePdfUri != null) {
        var selectedFormat by remember { mutableStateOf(ImageExportFormat.JPG) }

        AlertDialog(
            onDismissRequest = { showPdfToImagesDialog = false },
            title = { Text("PDF to Images") },
            text = {
                Column {
                    Text("Every page will be saved as a separate image in your gallery (Pictures/myPrev). Choose the image format:")
                    Spacer(modifier = Modifier.height(8.dp))
                    ImageExportFormat.entries.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFormat = f }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == f,
                                onClick = { selectedFormat = f }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(f.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPdfToImagesDialog = false
                        viewModel?.exportPdfToImages(selectedSinglePdfUri!!, selectedFormat)
                    }
                ) {
                    Text("Export", fontWeight = FontWeight.Bold, color = BrandIndigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPdfToImagesDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Watermark Dialog
    if (showWatermarkDialog && selectedSinglePdfUri != null) {
        var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }

        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Add Watermark") },
            text = {
                Column {
                    Text("Enter watermark text to stamp across all pages:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWatermarkDialog = false
                        viewModel?.applyWatermark(selectedSinglePdfUri!!, watermarkText)
                    }
                ) {
                    Text("Apply Watermark", fontWeight = FontWeight.Bold, color = BrandIndigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Processing Dialog
    if (operationState is ToolOperationState.Processing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Processing Document") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = BrandIndigo)
                    Text(operationState.progressMessage)
                }
            },
            confirmButton = {}
        )
    }

    // Success Dialog
    if (operationState is ToolOperationState.Success) {
        AlertDialog(
            onDismissRequest = { viewModel?.resetState() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(36.dp)) },
            title = { Text(operationState.title, fontWeight = FontWeight.Bold) },
            text = { Text(operationState.message) },
            confirmButton = {
                Button(
                    onClick = {
                        val uriStr = operationState.resultUri.toString()
                        viewModel?.resetState()
                        onOpenDocumentInViewer(uriStr)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text("Open PDF Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel?.resetState() }) {
                    Text("Done")
                }
            }
        )
    }

    // Compress Success Dialog
    if (operationState is ToolOperationState.CompressSuccess) {
        val original = Formatter.formatFileSize(context, operationState.result.originalSizeBytes)
        val compressed = Formatter.formatFileSize(context, operationState.result.compressedSizeBytes)
        val savedPercent = if (operationState.result.originalSizeBytes > 0) {
            ((1f - (operationState.result.compressedSizeBytes.toFloat() / operationState.result.originalSizeBytes.toFloat())) * 100).toInt()
        } else 0

        AlertDialog(
            onDismissRequest = { viewModel?.resetState() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(36.dp)) },
            title = { Text("Compression Complete", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Original Size: $original")
                    Text("Compressed Size: $compressed", fontWeight = FontWeight.Bold, color = BrandTeal)
                    if (savedPercent > 0) {
                        Text("Reduced by $savedPercent%!", color = BrandIndigo, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uriStr = operationState.result.outputUri.toString()
                        viewModel?.resetState()
                        onOpenDocumentInViewer(uriStr)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text("Open PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel?.resetState() }) {
                    Text("Done")
                }
            }
        )
    }

    // Extract Text Success Dialog
    if (operationState is ToolOperationState.TextSuccess) {
        val textResult = operationState.result
        val suggestedName = textResult.outputFile.name.replace(Regex("_\\d{10,}(?=\\.txt$)"), "")

        AlertDialog(
            onDismissRequest = { viewModel?.resetState() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(36.dp)) },
            title = { Text("Text Extracted", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${textResult.pageCount} page(s), ${textResult.characterCount} characters.")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = textResult.preview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 8,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSaveFile = textResult.outputFile
                        viewModel?.resetState()
                        saveTextLauncher.launch(suggestedName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text("Save .txt")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel?.resetState()
                            try {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, textResult.outputUri)
                                    clipData = ClipData.newRawUri("", textResult.outputUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, "Share extracted text").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not share the file.", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text("Share") }
                    TextButton(onClick = { viewModel?.resetState() }) { Text("Done") }
                }
            }
        )
    }

    // Protect Success Dialog
    if (operationState is ToolOperationState.ProtectSuccess) {
        val protectedFile = operationState.outputFile
        val protectedUri = operationState.outputUri
        val suggestedName = protectedFile.name.replace(Regex("_\\d{10,}(?=\\.pdf$)"), "")

        AlertDialog(
            onDismissRequest = { viewModel?.resetState() },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(36.dp)) },
            title = { Text("PDF Protected", fontWeight = FontWeight.Bold) },
            text = {
                Text("Your PDF is now encrypted with AES-256. Save or share it, then open it in any PDF reader using the password you set.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSaveFile = protectedFile
                        viewModel?.resetState()
                        savePdfLauncher.launch(suggestedName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text("Save to Device")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel?.resetState()
                            try {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, protectedUri)
                                    clipData = ClipData.newRawUri("", protectedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, "Share protected PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not share the file.", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text("Share") }
                    TextButton(onClick = { viewModel?.resetState() }) { Text("Done") }
                }
            }
        )
    }

    // PDF to Images Success Dialog
    if (operationState is ToolOperationState.ImagesSuccess) {
        val result = operationState.result
        AlertDialog(
            onDismissRequest = { viewModel?.resetState() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(36.dp)) },
            title = { Text("Export Complete", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${result.imageCount} page image(s) saved.")
                    Text(result.locationLabel, style = MaterialTheme.typography.bodySmall, color = BrandTeal)
                }
            },
            confirmButton = {
                if (result.firstImageUri != null) {
                    Button(
                        onClick = {
                            val imageUri = result.firstImageUri
                            val mime = result.mimeType
                            viewModel?.resetState()
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(imageUri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "No gallery app found. Open the Pictures/myPrev folder manually.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                    ) {
                        Text("View Images")
                    }
                } else {
                    TextButton(onClick = { viewModel?.resetState() }) { Text("OK") }
                }
            },
            dismissButton = {
                if (result.firstImageUri != null) {
                    TextButton(onClick = { viewModel?.resetState() }) { Text("Done") }
                }
            }
        )
    }

    // Error Dialog
    if (operationState is ToolOperationState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel?.resetState() },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Tool Operation Failed") },
            text = { Text(operationState.message) },
            confirmButton = {
                TextButton(onClick = { viewModel?.resetState() }) { Text("Dismiss") }
            }
        )
    }
}
