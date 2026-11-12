package com.medipote.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File
import java.util.UUID

/** Photos and imported files of the health record, kept in the app's private storage. */
object DocumentStore {

    fun dir(context: Context): File = File(context.filesDir, "documents").apply { mkdirs() }

    fun file(context: Context, doc: HealthDocument): File = File(dir(context), doc.fileName)

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** A new empty file for the camera to write a photo into. */
    fun newPhotoFile(context: Context): File = File(dir(context), "${UUID.randomUUID()}.jpg")

    /** Copies a picked file into the app; returns its stored name and type, or null on failure. */
    fun import(context: Context, uri: Uri): Pair<String, String>? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val name = "${UUID.randomUUID()}.$ext"
        resolver.openInputStream(uri)!!.use { input -> File(dir(context), name).outputStream().use { input.copyTo(it) } }
        name to mime
    }.getOrNull()

    fun delete(context: Context, doc: HealthDocument) {
        file(context, doc).delete()
    }

    fun shareIntent(context: Context, doc: HealthDocument): Intent {
        val uri = uriFor(context, file(context, doc))
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(doc.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, doc.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Partager le document",
        )
    }

    /** First page of a PDF or a downscaled, correctly rotated photo. */
    fun thumbnail(context: Context, doc: HealthDocument, maxPx: Int): Bitmap? = runCatching {
        val f = file(context, doc)
        if (!f.exists()) return null
        if (doc.isPdf) renderPdf(f, maxPx, maxPages = 1).firstOrNull() else decodeImage(f, maxPx)
    }.getOrNull()

    fun pages(context: Context, doc: HealthDocument, widthPx: Int): List<Bitmap> = runCatching {
        val f = file(context, doc)
        if (doc.isPdf) renderPdf(f, widthPx, maxPages = 30) else listOfNotNull(decodeImage(f, widthPx * 2))
    }.getOrDefault(emptyList())

    private fun decodeImage(f: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = when (runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (rotation == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }

    private fun renderPdf(f: File, widthPx: Int, maxPages: Int): List<Bitmap> =
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until minOf(renderer.pageCount, maxPages)).map { i ->
                    renderer.openPage(i).use { page ->
                        val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        createBitmap(widthPx, h).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
}
