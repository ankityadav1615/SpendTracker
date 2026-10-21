package com.spendtracker.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Stores receipt photos privately under the app's files directory, keyed by a plain absolute path (the
 * app is their only reader, so no content:// Uri is needed except transiently to hand the camera app a
 * writable target). Photos are downscaled on save so a few receipts don't add up to much storage.
 */
object ReceiptStorage {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85

    private fun dir(context: Context): File = File(context.filesDir, "receipts").apply { mkdirs() }

    /** A fresh file plus a FileProvider Uri the camera app can write a capture into. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(dir(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Deletes every stored receipt photo, for when the user deletes all their data. */
    fun deleteAll(context: Context) {
        File(context.filesDir, "receipts").deleteRecursively()
    }

    /** Downscales a just-captured photo in place. Call after [newCaptureTarget]'s file has been written. */
    fun finishCapture(file: File) {
        downscale(file)
    }

    /** Copies a gallery-picked image into app-private storage, downscaled. Returns its path, or null on failure. */
    fun copyPicked(context: Context, source: Uri): String? {
        val file = File(dir(context), "${UUID.randomUUID()}.jpg")
        return try {
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) return null
            downscale(file)
            file.absolutePath
        } catch (e: IOException) {
            file.delete()
            null
        }
    }

    private fun downscale(file: File) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
            val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                ?: return
            val oriented = applyExifRotation(file, decoded)
            file.outputStream().use { out -> oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            if (oriented !== decoded) decoded.recycle()
            oriented.recycle()
        } catch (e: IOException) {
            // Keep the original, undownscaled file rather than losing the receipt.
        }
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val degrees = try {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: IOException) {
            0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
