package com.example.feature_tools.domain

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.example.core.pdf.PdfEngine
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/**
 * dpi = pages kis resolution par render hongay (PDF points 72 dpi par hote hain).
 * jpegQuality = har page image ki JPEG quality.
 * Order important hai: LOW -> HIGH (compression strength barhti hai).
 */
enum class CompressQuality(val label: String, val dpi: Int, val jpegQuality: Int) {
    LOW("Standard (Light reduction)", 150, 80),
    MEDIUM("Medium (Balanced)", 110, 60),
    HIGH("High (Maximum compression)", 72, 45)
}

enum class ImageExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val compressFormat: Bitmap.CompressFormat,
    val quality: Int
) {
    PNG("PNG (Lossless, larger files)", "png", "image/png", Bitmap.CompressFormat.PNG, 100),
    JPG("JPG (Smaller files)", "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92)
}

data class ImageExportResult(
    val imageCount: Int,
    /** User ko dikhane ke liye folder ka path. */
    val locationLabel: String,
    /** Gallery me kholne ke liye pehli image (sirf Android 10+ par, MediaStore URI). */
    val firstImageUri: Uri?,
    val mimeType: String
)

data class TextExtractResult(
    val outputFile: File,
    val outputUri: Uri,
    val pageCount: Int,
    /** Whitespace ke baghair characters ki ginti. */
    val characterCount: Int,
    val preview: String
)

/** Jab kisi bhi level par output original se chhota na ban sake. */
class AlreadyOptimizedException(message: String) : Exception(message)

data class CompressResult(
    val outputFile: File,
    val outputUri: Uri,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long
)

object PdfToolsEngine {

    private fun getToolsOutputDir(context: Context): File {
        return File(context.filesDir, "tools_output").apply { mkdirs() }
    }

    /**
     * Merge multiple PDF files into one continuous document.
     */
    suspend fun mergePdfs(
        context: Context,
        uris: List<Uri>,
        outputTitle: String = "Merged_Document"
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        try {
            if (uris.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No documents selected"))
            }

            val doc = PdfDocument()
            var globalPageIndex = 1

            for (uri in uris) {
                val tempFile = PdfEngine.copyUriToTempFile(context, uri)
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val width = page.width
                    val height = page.height

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, globalPageIndex++).create()
                    val newPage = doc.startPage(pageInfo)
                    newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(newPage)
                }

                renderer.close()
                pfd.close()
            }

            val outputFile = File(getToolsOutputDir(context), "${outputTitle}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
            Result.success(Pair(outputFile, uri))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Split / extract a page range from a PDF.
     */
    suspend fun splitPdf(
        context: Context,
        uri: Uri,
        fromPage: Int,
        toPage: Int
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempFile = PdfEngine.copyUriToTempFile(context, uri)
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            val safeStart = (fromPage - 1).coerceIn(0, renderer.pageCount - 1)
            val safeEnd = (toPage - 1).coerceIn(safeStart, renderer.pageCount - 1)

            val doc = PdfDocument()
            var newPageIndex = 1

            for (p in safeStart..safeEnd) {
                val page = renderer.openPage(p)
                val width = page.width
                val height = page.height

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageIndex++).create()
                val newPage = doc.startPage(pageInfo)
                newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                doc.finishPage(newPage)
            }

            renderer.close()
            pfd.close()

            val outputFile = File(getToolsOutputDir(context), "split_p${fromPage}_p${toPage}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
            Result.success(Pair(outputFile, resultUri))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compress a PDF by re-rendering every page and storing it as a JPEG image inside a new PDF.
     *
     * Do bunyadi masle fix kiye gaye hain:
     * 1. PdfRenderer sirf ARGB_8888 bitmap accept karta hai (pehle RGB_565 tha -> "Unsupported pixel format" crash).
     * 2. Android ka PdfDocument bitmaps ko lossless (Flate) save karta hai, is liye output aksar original se BARA ban jata tha.
     *    Ab hum chhota sa PDF writer use kar rahe hain jo pages ko asal JPEG (DCTDecode) stream ke taur par likhta hai.
     *
     * Agar chosen level par size kam na ho to agla strong level try hota hai; phir bhi kam na ho to
     * [AlreadyOptimizedException] return hoti hai.
     */
    suspend fun compressPdf(
        context: Context,
        uri: Uri,
        quality: CompressQuality
    ): Result<CompressResult> = withContext(Dispatchers.IO) {
        var candidate: File? = null
        try {
            val sourceFile = PdfEngine.copyUriToTempFile(context, uri)
            val originalSize = sourceFile.length()
            val outputDir = getToolsOutputDir(context)

            var best: File? = null
            for (level in CompressQuality.entries.filter { it.ordinal >= quality.ordinal }) {
                val file = File(outputDir, "compressed_${System.currentTimeMillis()}.pdf")
                candidate = file
                writeCompressedPdf(sourceFile, file, level)
                if (file.length() in 1 until originalSize) {
                    best = file
                    break
                }
                file.delete()
                candidate = null
            }

            if (best == null) {
                return@withContext Result.failure(
                    AlreadyOptimizedException(
                        "This PDF is already well optimized (mostly text/vector content), so compressing it would not make it smaller."
                    )
                )
            }

            val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", best)
            Result.success(
                CompressResult(
                    outputFile = best,
                    outputUri = resultUri,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = best.length()
                )
            )
        } catch (e: CancellationException) {
            candidate?.delete()
            throw e
        } catch (e: OutOfMemoryError) {
            candidate?.delete()
            Result.failure(Exception("Not enough memory to compress this PDF. Try the 'High' option or a smaller file."))
        } catch (e: Exception) {
            candidate?.delete()
            Result.failure(e)
        }
    }

    private const val MAX_RENDER_SIDE_PX = 2400

    /**
     * Har page ko JPEG bana kar minimal valid PDF (1.4) me likhta hai.
     * Pages ek ek kar ke stream hote hain, is liye memory me sirf ek page bitmap hota hai.
     */
    private fun CoroutineScope.writeCompressedPdf(source: File, target: File, level: CompressQuality) {
        val pfd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val pageCount = renderer.pageCount
            require(pageCount > 0) { "This PDF has no pages." }

            // Object ids: 1 = Catalog, 2 = Pages, phir har page ke liye 3 objects (Page, Content, Image)
            val objectCount = 2 + pageCount * 3
            val offsets = LongArray(objectCount + 1)

            BufferedOutputStream(FileOutputStream(target)).use { stream ->
                val out = CountingWriter(stream)

                out.write("%PDF-1.4\n")
                out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

                offsets[1] = out.position
                out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

                val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 3} 0 R" }
                offsets[2] = out.position
                out.write("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $pageCount >>\nendobj\n")

                for (i in 0 until pageCount) {
                    ensureActive()

                    val pageId = 3 + i * 3
                    val contentId = pageId + 1
                    val imageId = pageId + 2

                    val page = renderer.openPage(i)
                    val widthPt = page.width
                    val heightPt = page.height
                    val bitmap = try {
                        var scale = level.dpi / 72f
                        val longestSide = max(widthPt, heightPt) * scale
                        if (longestSide > MAX_RENDER_SIDE_PX) scale *= MAX_RENDER_SIDE_PX / longestSide

                        val bmp = Bitmap.createBitmap(
                            (widthPt * scale).roundToInt().coerceAtLeast(1),
                            (heightPt * scale).roundToInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888 // PdfRenderer sirf yehi config support karta hai
                        )
                        bmp.eraseColor(Color.WHITE) // render() background paint nahi karta
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally {
                        page.close()
                    }

                    val pixelWidth = bitmap.width
                    val pixelHeight = bitmap.height
                    val jpegBytes = ByteArrayOutputStream().use { buffer ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, buffer)
                        buffer.toByteArray()
                    }
                    bitmap.recycle()

                    offsets[pageId] = out.position
                    out.write(
                        "$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $widthPt $heightPt] " +
                            "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
                    )

                    val content = "q $widthPt 0 0 $heightPt 0 0 cm /Im0 Do Q\n"
                    offsets[contentId] = out.position
                    out.write("$contentId 0 obj\n<< /Length ${content.length} >>\nstream\n$content" + "endstream\nendobj\n")

                    offsets[imageId] = out.position
                    out.write(
                        "$imageId 0 obj\n<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight " +
                            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
                    )
                    out.write(jpegBytes)
                    out.write("\nendstream\nendobj\n")
                }

                val xrefPosition = out.position
                out.write("xref\n0 ${objectCount + 1}\n")
                out.write("0000000000 65535 f \n")
                for (id in 1..objectCount) {
                    // Locale.US zaroori hai, warna kuch locales me digits Arabic-Indic ban jate hain
                    out.write(String.format(Locale.US, "%010d 00000 n \n", offsets[id]))
                }
                out.write("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R >>\nstartxref\n$xrefPosition\n%%EOF\n")
            }
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    /** Output stream wrapper jo likhe hue bytes ka offset (xref ke liye) track karta hai. */
    private class CountingWriter(private val out: OutputStream) {
        var position = 0L
            private set

        fun write(bytes: ByteArray) {
            out.write(bytes)
            position += bytes.size
        }

        fun write(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * PDF ko AES-256 (PDF 2.0, revision 6) password encryption se protect karta hai.
     *
     * PdfBox-Android use hota hai (project me pehle se maujood), is liye pages ko image banaye baghair
     * original text/vector content waisa hi rehta hai (select/search/zoom quality barqarar).
     * Password ke baghair file khulti nahi; same password user aur owner dono ke liye set hota hai.
     *
     * NOTE: Android ka PdfRenderer encrypted PDFs render nahi kar sakta, is liye in-app viewer me
     * protected file nahi khulegi - user isay share/save kar ke kisi bhi PDF reader me password se khole.
     */
    suspend fun protectPdf(
        context: Context,
        uri: Uri,
        password: String
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        try {
            require(password.isNotEmpty()) { "Password cannot be empty." }

            val sourceFile = PdfEngine.copyUriToTempFile(context, uri)
            val baseName = resolveBaseName(context, uri)
            val target = File(getToolsOutputDir(context), "${baseName}_protected_${System.currentTimeMillis()}.pdf")
            outputFile = target

            val document = try {
                PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw IllegalStateException("This PDF is already protected with a password, so it cannot be protected again.", e)
            }

            try {
                ensureActive()
                val permissions = AccessPermission() // sab permissions allowed; lock sirf password se
                val policy = StandardProtectionPolicy(password, password, permissions).apply {
                    encryptionKeyLength = 256 // 256-bit key => AES-256
                }
                document.protect(policy)
                document.save(target)
            } finally {
                try { document.close() } catch (_: Exception) {}
            }

            if (target.length() == 0L) throw java.io.IOException("Failed to write the protected PDF.")

            val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            Result.success(Pair(target, resultUri))
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: OutOfMemoryError) {
            outputFile?.delete()
            Result.failure(Exception("Not enough memory to protect this PDF."))
        } catch (e: Exception) {
            outputFile?.delete()
            Result.failure(e)
        }
    }

    /**
     * PDF ke selectable text ko .txt file me nikalta hai (PdfBox-Android PDFTextStripper).
     * Page by page chalta hai, is liye bari files me bhi poora text memory me jama nahi hota.
     * Scanned/image-only PDFs me text nahi hota (OCR support nahi) - us surat me saaf error milta hai.
     */
    suspend fun extractText(
        context: Context,
        uri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<TextExtractResult> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        try {
            val sourceFile = PdfEngine.copyUriToTempFile(context, uri)
            val baseName = resolveBaseName(context, uri)
            val target = File(getToolsOutputDir(context), "${baseName}_text_${System.currentTimeMillis()}.txt")
            outputFile = target

            val document = try {
                PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw IllegalStateException("This PDF is password protected, so its text cannot be extracted.", e)
            }

            var totalChars = 0
            var pageCount = 0
            val preview = StringBuilder()
            try {
                pageCount = document.numberOfPages
                require(pageCount > 0) { "This PDF has no pages." }

                val stripper = PDFTextStripper().apply { sortByPosition = true }

                target.bufferedWriter(Charsets.UTF_8).use { writer ->
                    for (page in 1..pageCount) {
                        ensureActive()
                        onProgress(page, pageCount)

                        stripper.startPage = page
                        stripper.endPage = page
                        val text = stripper.getText(document).trim()

                        if (pageCount > 1) {
                            if (page > 1) writer.write("\n\n")
                            writer.write("----- Page $page -----\n\n")
                        }
                        writer.write(text)

                        totalChars += text.count { !it.isWhitespace() }
                        if (preview.length < PREVIEW_CHARS) {
                            preview.append(text.take(PREVIEW_CHARS - preview.length)).append('\n')
                        }
                    }
                }
            } finally {
                try { document.close() } catch (_: Exception) {}
            }

            if (totalChars == 0) {
                target.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        "No selectable text found. This PDF looks like a scan or image-only document (OCR is not supported)."
                    )
                )
            }

            val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            Result.success(
                TextExtractResult(
                    outputFile = target,
                    outputUri = resultUri,
                    pageCount = pageCount,
                    characterCount = totalChars,
                    preview = preview.toString().trim()
                )
            )
        } catch (e: CancellationException) {
            outputFile?.delete()
            throw e
        } catch (e: OutOfMemoryError) {
            outputFile?.delete()
            Result.failure(Exception("Not enough memory to extract text from this PDF."))
        } catch (e: Exception) {
            outputFile?.delete()
            Result.failure(e)
        }
    }

    private const val PREVIEW_CHARS = 400
    private const val EXPORT_SCALE = 2f          // 72dpi * 2 = 144dpi
    private const val EXPORT_MAX_SIDE_PX = 3000

    /**
     * Har PDF page ko alag image (PNG/JPG) bana kar Pictures/myPrev/<pdf-naam>_<time>/ me save karta hai.
     * Android 10+ par MediaStore use hota hai (koi storage permission nahi chahiye, images gallery me nazar aati hain).
     * Android 9 aur neeche app ki external Pictures directory me save hota hai (is me bhi permission nahi chahiye).
     * Beech me fail ho to ab tak bani images delete kar di jati hain.
     */
    suspend fun pdfToImages(
        context: Context,
        uri: Uri,
        format: ImageExportFormat,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ImageExportResult> = withContext(Dispatchers.IO) {
        val createdUris = mutableListOf<Uri>()
        val createdFiles = mutableListOf<File>()
        try {
            val sourceFile = PdfEngine.copyUriToTempFile(context, uri)
            val baseName = resolveBaseName(context, uri)
            val folderName = "${baseName}_${System.currentTimeMillis()}"
            val useMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            val localDir: File? = if (useMediaStore) null else {
                val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: File(context.filesDir, "Pictures")
                File(pictures, "myPrev/$folderName").apply { mkdirs() }
            }

            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                val total = renderer.pageCount
                require(total > 0) { "This PDF has no pages." }

                var firstUri: Uri? = null
                for (i in 0 until total) {
                    ensureActive()
                    onProgress(i + 1, total)

                    val bitmap = renderPageBitmap(renderer, i, EXPORT_SCALE, EXPORT_MAX_SIDE_PX)
                    try {
                        val fileName = "${baseName}_page_${i + 1}.${format.extension}"
                        if (useMediaStore) {
                            val saved = saveToMediaStore(context, bitmap, format, folderName, fileName)
                            createdUris += saved
                            if (firstUri == null) firstUri = saved
                        } else {
                            val file = File(localDir!!, fileName)
                            createdFiles += file
                            FileOutputStream(file).use { out ->
                                if (!bitmap.compress(format.compressFormat, format.quality, out)) {
                                    throw java.io.IOException("Failed to encode page ${i + 1}")
                                }
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                val label = if (useMediaStore) {
                    "Pictures/myPrev/$folderName"
                } else {
                    localDir!!.absolutePath
                }
                Result.success(ImageExportResult(total, label, firstUri, format.mimeType))
            } finally {
                try { renderer.close() } catch (_: Exception) {}
                try { pfd.close() } catch (_: Exception) {}
            }
        } catch (e: CancellationException) {
            cleanupExport(context, createdUris, createdFiles)
            throw e
        } catch (e: OutOfMemoryError) {
            cleanupExport(context, createdUris, createdFiles)
            Result.failure(Exception("Not enough memory to export this PDF. Try the JPG format."))
        } catch (e: Exception) {
            cleanupExport(context, createdUris, createdFiles)
            Result.failure(e)
        }
    }

    /** Page ko ARGB_8888 bitmap me render karta hai (white background ke sath). */
    private fun renderPageBitmap(renderer: PdfRenderer, index: Int, scale: Float, maxSide: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            var s = scale
            val longest = max(page.width, page.height) * s
            if (longest > maxSide) s *= maxSide / longest

            val bitmap = Bitmap.createBitmap(
                (page.width * s).roundToInt().coerceAtLeast(1),
                (page.height * s).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    private fun saveToMediaStore(
        context: Context,
        bitmap: Bitmap,
        format: ImageExportFormat,
        folderName: String,
        fileName: String
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/myPrev/$folderName")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Could not create image file in gallery.")
        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                if (!bitmap.compress(format.compressFormat, format.quality, out)) {
                    throw java.io.IOException("Failed to encode image.")
                }
            } ?: throw java.io.IOException("Could not open image file for writing.")

            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(itemUri, done, null, null)
            return itemUri
        } catch (e: Exception) {
            try { resolver.delete(itemUri, null, null) } catch (_: Exception) {}
            throw e
        }
    }

    private fun cleanupExport(context: Context, uris: List<Uri>, files: List<File>) {
        uris.forEach { try { context.contentResolver.delete(it, null, null) } catch (_: Exception) {} }
        files.forEach { try { it.delete() } catch (_: Exception) {} }
        files.firstOrNull()?.parentFile?.let { try { it.delete() } catch (_: Exception) {} }
    }

    private fun resolveBaseName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0)
                }
            } catch (_: Exception) { /* permission/column na mile to default naam */ }
        } else if (uri.scheme == "file") {
            name = File(uri.path ?: "").name
        }
        val clean = (name ?: "PDF")
            .removeSuffix(".pdf").removeSuffix(".PDF")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.')
            .take(40)
        return clean.ifEmpty { "PDF" }
    }

    /**
     * Convert list of image URIs to a multi-page PDF document.
     */
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputTitle: String = "Images_Converted"
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        try {
            if (imageUris.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No images selected"))
            }

            val doc = PdfDocument()
            val a4Width = 595
            val a4Height = 842

            var pageNumber = 1
            for (imgUri in imageUris) {
                var bitmap: Bitmap? = null
                context.contentResolver.openInputStream(imgUri)?.use { input: InputStream ->
                    bitmap = BitmapFactory.decodeStream(input)
                }

                if (bitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, pageNumber++).create()
                    val page = doc.startPage(pageInfo)
                    val canvas = page.canvas

                    val scale = min(
                        (a4Width - 40).toFloat() / bitmap!!.width.toFloat(),
                        (a4Height - 40).toFloat() / bitmap!!.height.toFloat()
                    )
                    val drawWidth = (bitmap!!.width * scale).toInt()
                    val drawHeight = (bitmap!!.height * scale).toInt()
                    val posX = (a4Width - drawWidth) / 2f
                    val posY = (a4Height - drawHeight) / 2f

                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, drawWidth, drawHeight, true)
                    canvas.drawBitmap(scaledBitmap, posX, posY, null)
                    doc.finishPage(page)
                }
            }

            val outputFile = File(getToolsOutputDir(context), "${outputTitle}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
            Result.success(Pair(outputFile, resultUri))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add customizable diagonal watermark text across all pages.
     */
    suspend fun addWatermark(
        context: Context,
        uri: Uri,
        watermarkText: String
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempFile = PdfEngine.copyUriToTempFile(context, uri)
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            val doc = PdfDocument()

            val watermarkPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#334F46E5") // Semi-transparent brand indigo
                textSize = 46f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            for (pageIndex in 0 until renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                val width = page.width
                val height = page.height

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create()
                val newPage = doc.startPage(pageInfo)
                val newCanvas = newPage.canvas

                newCanvas.drawBitmap(bitmap, 0f, 0f, null)

                // Draw diagonal watermark
                newCanvas.save()
                newCanvas.rotate(-35f, width / 2f, height / 2f)
                newCanvas.drawText(watermarkText, width / 2f, height / 2f, watermarkPaint)
                newCanvas.restore()

                doc.finishPage(newPage)
            }

            renderer.close()
            pfd.close()

            val outputFile = File(getToolsOutputDir(context), "watermarked_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
            Result.success(Pair(outputFile, resultUri))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
