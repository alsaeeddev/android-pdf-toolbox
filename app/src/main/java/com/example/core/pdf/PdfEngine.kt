package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.content.FileProvider
import com.example.core.data.local.entity.PdfDocumentEntity
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Password-protected PDF ko kholne ke liye password chahiye (ya diya gaya password ghalat hai).
 * Android ka PdfRenderer is surat me SecurityException deta hai, jo pehle galti se "permission error" samjha jata tha.
 */
class PdfPasswordRequiredException(val incorrectPassword: Boolean = false) :
    Exception(if (incorrectPassword) "Incorrect password" else "This PDF is password protected")

/** True agar exception PdfRenderer ke "password required / incorrect password" error ki hai (permission error ki nahi). */
fun Throwable.isPdfPasswordError(): Boolean {
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 5) {
        if (t is PdfPasswordRequiredException || t is InvalidPasswordException) return true
        if (t is SecurityException && t.message?.contains("password", ignoreCase = true) == true) return true
        t = t.cause
        depth++
    }
    return false
}

/**
 * Sirf memory me (disk par kabhi nahi) user ka diya hua password rakhta hai taake Viewer se Editor kholne par
 * dobara password na poochna pare. App process band hote hi ye khatam ho jata hai.
 */
object PdfPasswordStore {
    private val passwords = ConcurrentHashMap<String, String>()
    fun get(uri: String): String? = passwords[uri]
    fun put(uri: String, password: String) { passwords[uri] = password }
    fun remove(uri: String) { passwords.remove(uri) }
}

object PdfEngine {

    /**
     * Resolves metadata (name, size, page count) for a PDF URI.
     */
    suspend fun inspectPdf(
        context: Context,
        uri: Uri,
        forceRefresh: Boolean = false
    ): Result<PdfDocumentEntity> = withContext(Dispatchers.IO) {
        try {
            var fileName = "Document.pdf"
            var fileSize = 0L

            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (_: SecurityException) {
                    // Permission expire ho gayi: persistent copy se kaam chalayenge
                }
            } else if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    fileName = file.name
                    fileSize = file.length()
                }
            }

            // Obtain page count using PdfRenderer safely
            val localFile = copyUriToTempFile(context, uri, forceRefresh)
            var pageCount = 0
            val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                try {
                    pageCount = renderer.pageCount
                } finally {
                    renderer.close()
                }
            } catch (e: Exception) {
                // Password-protected PDF: page count abhi maloom nahi (unlock ke baad update hota hai).
                // Viewer is file par password dialog dikhayega, is liye yahan failure nahi dete.
                if (!e.isPdfPasswordError()) throw e
            } finally {
                try { pfd.close() } catch (_: Exception) {}
            }

            if (fileSize == 0L) {
                fileSize = localFile.length()
            }

            Result.success(
                PdfDocumentEntity(
                    uri = uri.toString(),
                    name = fileName,
                    sizeBytes = fileSize,
                    pageCount = pageCount,
                    lastOpenedTimestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCacheForUri(context: Context, uri: Uri) {
        try {
            val dir = File(context.filesDir, STORE_DIR)
            File(dir, "doc_${uri.toString().hashCode()}.pdf").delete()
            File(dir, "doc_${uri.toString().hashCode()}_tmp.pdf").delete()
        } catch (_: Exception) {}
    }

    /**
     * Copies a content URI or remote stream to an app-private cache file for fast, repeated read access.
     */
    suspend fun copyUriToTempFile(
        context: Context,
        uri: Uri,
        forceRefresh: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file" || uri.scheme == null) {
            val file = File(uri.path ?: "")
            if (file.exists() && file.canRead()) {
                return@withContext file
            }
        }

        // filesDir use kiya hai (cacheDir nahi) taake OS storage kam hone par copy delete na kare.
        val storeDir = File(context.filesDir, STORE_DIR).apply { mkdirs() }
        val storedFile = File(storeDir, "doc_${uri.toString().hashCode()}.pdf")

        // Already stored copy hai to original URI ki permission ki zaroorat hi nahi.
        if (!forceRefresh && storedFile.exists() && storedFile.length() > 0) {
            return@withContext storedFile
        }

        val tmpFile = File(storeDir, "doc_${uri.toString().hashCode()}_tmp.pdf")
        if (tmpFile.exists()) tmpFile.delete()

        try {
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw SecurityException("Could not open input stream for URI: $uri")

            if (tmpFile.exists() && tmpFile.length() > 0) {
                if (storedFile.exists()) storedFile.delete()
                tmpFile.renameTo(storedFile)
            } else {
                tmpFile.delete()
                throw SecurityException("Could not copy document data from URI: $uri")
            }
        } catch (e: Exception) {
            tmpFile.delete()
            // Refresh fail hua (permission expire) magar purani copy maujood hai -> wahi use karo.
            if (storedFile.exists() && storedFile.length() > 0) {
                return@withContext storedFile
            }
            when (e) {
                is SecurityException ->
                    throw SecurityException("Permission denied to access document: ${e.localizedMessage}", e)
                is FileNotFoundException ->
                    throw SecurityException("File not found or permission denied for URI: $uri", e)
                else -> throw e
            }
        }

        storedFile
    }

    private const val STORE_DIR = "pdf_store"
    private const val UNLOCKED_DIR = "unlocked_pdfs"

    /** Decrypted (viewing-only) copies sirf cacheDir me, session band hone par delete ho jati hain. */
    internal fun unlockedFileFor(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, UNLOCKED_DIR).apply { mkdirs() }
        return File(dir, "unlocked_${uri.toString().hashCode()}.pdf")
    }

    /** App start par purani (crash/kill ki wajah se reh gayi) decrypted copies saaf karta hai. */
    fun clearUnlockedCache(context: Context) {
        try {
            File(context.cacheDir, UNLOCKED_DIR).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /**
     * Creates a rich 3-page myPrev User Guide PDF in local files directory if it doesn't already exist.
     */
    suspend fun createSampleGuidePdf(context: Context): Uri = withContext(Dispatchers.IO) {
        val guidesDir = File(context.filesDir, "sample_docs").apply { mkdirs() }
        val sampleFile = File(guidesDir, "myPrev_User_Guide.pdf")

        if (sampleFile.exists() && sampleFile.length() > 0) {
            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sampleFile
            )
        }

        val doc = PdfDocument()
        val pageWidth = 595 // Standard A4 points (72 dpi)
        val pageHeight = 842

        // Paint setup
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#4F46E5") // Brand Indigo
            textSize = 28f
            isFakeBoldText = true
        }

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#14B8A6") // Brand Teal
            textSize = 18f
            isFakeBoldText = true
        }

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#1E293B")
            textSize = 13f
        }

        val cardBgPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#F1F5F9")
        }

        val accentCardPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#EEF2FF")
        }

        // ================= PAGE 1: Welcome & Viewer =================
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = doc.startPage(pageInfo1)
        val canvas1 = page1.canvas

        // Header
        canvas1.drawRoundRect(40f, 40f, (pageWidth - 40).toFloat(), 150f, 16f, 16f, accentCardPaint)
        canvas1.drawText("myPrev", 60f, 90f, titlePaint)
        canvas1.drawText("Welcome to Modern PDF Viewing & Editing", 60f, 125f, subtitlePaint)

        // Section 1
        canvas1.drawRoundRect(40f, 170f, (pageWidth - 40).toFloat(), 380f, 16f, 16f, cardBgPaint)
        subtitlePaint.color = AndroidColor.parseColor("#4F46E5")
        canvas1.drawText("1. High-Performance Viewing Experience", 60f, 210f, subtitlePaint)
        canvas1.drawText("- Smooth 60/120 fps continuous vertical scrolling and single-page horizontal mode.", 60f, 245f, bodyPaint)
        canvas1.drawText("- Precision pinch-to-zoom and double-tap zoom with multi-density caching.", 60f, 275f, bodyPaint)
        canvas1.drawText("- Instant full-text search with live hit counters and navigation.", 60f, 305f, bodyPaint)
        canvas1.drawText("- Customizable reading modes: Normal, Invert Dark, Sepia, and Night mode.", 60f, 335f, bodyPaint)
        canvas1.drawText("- Fast-scroller thumb scrubber and instant jump-to-page dialog.", 60f, 365f, bodyPaint)

        // Section 2
        canvas1.drawRoundRect(40f, 400f, (pageWidth - 40).toFloat(), 600f, 16f, 16f, cardBgPaint)
        canvas1.drawText("2. Bookmarks & Smart History", 60f, 440f, subtitlePaint)
        canvas1.drawText("- Bookmark important pages with custom titles and quickly jump to them.", 60f, 475f, bodyPaint)
        canvas1.drawText("- myPrev automatically remembers your last-read page for every opened file.", 60f, 505f, bodyPaint)
        canvas1.drawText("- Mark your most important documents as Favorites for 1-tap access.", 60f, 535f, bodyPaint)
        canvas1.drawText("- Filter and search through your recent documents instantly.", 60f, 565f, bodyPaint)

        // Footer
        canvas1.drawText("Page 1 of 3 - myPrev User Guide", (pageWidth / 2 - 80).toFloat(), (pageHeight - 40).toFloat(), bodyPaint)
        doc.finishPage(page1)

        // ================= PAGE 2: Annotations & Signatures =================
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = doc.startPage(pageInfo2)
        val canvas2 = page2.canvas

        canvas2.drawText("Annotation & Digital Signatures", 60f, 80f, titlePaint)
        canvas2.drawText("Mark up documents with studio-grade precision", 60f, 110f, subtitlePaint)

        canvas2.drawRoundRect(40f, 140f, (pageWidth - 40).toFloat(), 430f, 16f, 16f, cardBgPaint)
        canvas2.drawText("Creative Annotation Tools", 60f, 180f, subtitlePaint)
        canvas2.drawText("- Freehand Pen: High-precision strokes with Bezier smoothing and opacity control.", 60f, 215f, bodyPaint)
        canvas2.drawText("- Highlighter & Underline: Vibrant colors for key points and research study.", 60f, 245f, bodyPaint)
        canvas2.drawText("- Add Text: Insert styled text annotations anywhere on the canvas.", 60f, 275f, bodyPaint)
        canvas2.drawText("- Shapes & Stamps: Rectangles, arrows, lines, and status stamps (Approved, Draft).", 60f, 305f, bodyPaint)
        canvas2.drawText("- Complete Undo/Redo history stack with safe non-destructive autosaves.", 60f, 335f, bodyPaint)
        canvas2.drawText("- Non-destructive saving: never overwrites your original without confirmation.", 60f, 365f, bodyPaint)

        canvas2.drawRoundRect(40f, 460f, (pageWidth - 40).toFloat(), 670f, 16f, 16f, accentCardPaint)
        subtitlePaint.color = AndroidColor.parseColor("#14B8A6")
        canvas2.drawText("Reusable Digital Signatures", 60f, 500f, subtitlePaint)
        canvas2.drawText("- Draw your signature with your finger or stylus directly on device.", 60f, 535f, bodyPaint)
        canvas2.drawText("- Save multiple signatures into Room database for instant reuse.", 60f, 565f, bodyPaint)
        canvas2.drawText("- Position, resize, and rotate signatures to sign contracts in seconds.", 60f, 595f, bodyPaint)
        canvas2.drawText("- 100% offline security: your signatures never leave your device.", 60f, 625f, bodyPaint)

        canvas2.drawText("Page 2 of 3 - myPrev User Guide", (pageWidth / 2 - 80).toFloat(), (pageHeight - 40).toFloat(), bodyPaint)
        doc.finishPage(page2)

        // ================= PAGE 3: Offline Toolbox & Privacy =================
        val pageInfo3 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        val page3 = doc.startPage(pageInfo3)
        val canvas3 = page3.canvas

        canvas3.drawText("All-in-One PDF Toolbox", 60f, 80f, titlePaint)
        canvas3.drawText("Pro-grade document management without cloud dependencies", 60f, 110f, subtitlePaint)

        canvas3.drawRoundRect(40f, 140f, (pageWidth - 40).toFloat(), 480f, 16f, 16f, cardBgPaint)
        subtitlePaint.color = AndroidColor.parseColor("#4F46E5")
        canvas3.drawText("Built-in Utility Suite", 60f, 180f, subtitlePaint)
        canvas3.drawText("• Merge PDFs: Combine multiple documents into one structured file.", 60f, 215f, bodyPaint)
        canvas3.drawText("• Split & Extract: Extract selected page ranges or separate every N pages.", 60f, 250f, bodyPaint)
        canvas3.drawText("• Reorder & Rotate: Drag-and-drop page reorganization and orientation fixes.", 60f, 285f, bodyPaint)
        canvas3.drawText("• Compress PDF: Reduce document size for easy messaging and email sharing.", 60f, 320f, bodyPaint)
        canvas3.drawText("• Images to PDF: Convert photos, receipts, or screenshots into clean PDFs.", 60f, 355f, bodyPaint)
        canvas3.drawText("• PDF to Images: Export pages as crisp PNG or JPG image files.", 60f, 390f, bodyPaint)
        canvas3.drawText("• Password Protection: AES-256 encryption to secure confidential documents.", 60f, 425f, bodyPaint)
        canvas3.drawText("• Watermark: Add copyright, company stamps, or confidentiality notices.", 60f, 460f, bodyPaint)

        // Privacy banner
        canvas3.drawRoundRect(40f, 510f, (pageWidth - 40).toFloat(), 660f, 16f, 16f, accentCardPaint)
        subtitlePaint.color = AndroidColor.parseColor("#14B8A6")
        canvas3.drawText("Privacy First Architecture", 60f, 550f, subtitlePaint)
        canvas3.drawText("myPrev operates 100% offline with zero cloud telemetry. Your files,", 60f, 585f, bodyPaint)
        canvas3.drawText("passwords, and signatures remain strictly private on your device at all times.", 60f, 615f, bodyPaint)

        canvas3.drawText("Page 3 of 3 - myPrev User Guide", (pageWidth / 2 - 80).toFloat(), (pageHeight - 40).toFloat(), bodyPaint)
        doc.finishPage(page3)

        // Write to file
        FileOutputStream(sampleFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            sampleFile
        )
    }
}

/**
 * Lifecycle-safe, thread-safe PDF renderer wrapper around android.graphics.pdf.PdfRenderer.
 * Features an in-memory LRU Bitmap Cache to maintain fluid 60/120 fps rendering.
 */
class PdfRenderSession(
    private val context: Context,
    val uri: Uri
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    // 64MB LRU cache for rendered page bitmaps
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    var pageCount: Int = 0
        private set

    private var unlockedFile: File? = null

    /**
     * @param password protected PDF ke liye user ka password (null = koi password nahi diya).
     * Protected file par password na ho ya ghalat ho to [PdfPasswordRequiredException] ke saath failure milta hai.
     */
    suspend fun open(password: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                closeRenderer()
                val file = PdfEngine.copyUriToTempFile(context, uri)
                try {
                    openRenderer(file)
                } catch (e: Exception) {
                    if (!e.isPdfPasswordError()) throw e
                    closeRenderer()
                    if (password.isNullOrEmpty()) throw PdfPasswordRequiredException(false)

                    // PDFBox se password ke saath kholo aur security hata kar private cache me viewing copy banao.
                    val unlocked = PdfEngine.unlockedFileFor(context, uri)
                    try {
                        val doc = try {
                            PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
                        } catch (ipe: InvalidPasswordException) {
                            throw PdfPasswordRequiredException(true)
                        }
                        try {
                            doc.isAllSecurityToBeRemoved = true
                            doc.save(unlocked)
                        } finally {
                            try { doc.close() } catch (_: Exception) {}
                        }
                        unlockedFile = unlocked
                        openRenderer(unlocked)
                    } catch (t: Throwable) {
                        unlocked.delete()
                        unlockedFile = null
                        throw t
                    }
                }
                Result.success(pageCount)
            } catch (e: Exception) {
                closeRenderer()
                Result.failure(e)
            }
        }
    }

    private fun openRenderer(file: File) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(descriptor)
            pfd = descriptor
            renderer = r
            pageCount = r.pageCount
        } catch (e: Throwable) {
            try { descriptor.close() } catch (_: Exception) {}
            throw e
        }
    }

    private fun closeRenderer() {
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        renderer = null
        pfd = null
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "p_${pageIndex}_w_$targetWidth"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        mutex.withLock {
            val r = renderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

            var page: PdfRenderer.Page? = null
            try {
                page = r.openPage(pageIndex)
                val originalWidth = page.width
                val originalHeight = page.height

                val scale = targetWidth.toFloat() / originalWidth.toFloat()
                val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(AndroidColor.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                memoryCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                null
            } finally {
                page?.close()
            }
        }
    }

    fun close() {
        try {
            memoryCache.evictAll()
        } catch (_: Exception) {}
        closeRenderer()
        try { unlockedFile?.delete() } catch (_: Exception) {}
        unlockedFile = null
    }
}