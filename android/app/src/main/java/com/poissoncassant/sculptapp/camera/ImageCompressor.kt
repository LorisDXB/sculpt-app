package com.poissoncassant.sculptapp.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

object ImageCompressor {
  fun compressToJpeg(context: Context, sourceUri: Uri): File {
    val bitmap = decodeScaledBitmap(context, sourceUri)
    return writeCompressedBitmap(context, bitmap)
  }

  fun compressToJpeg(context: Context, sourceFile: File): File {
    val bitmap = decodeScaledBitmap(sourceFile)
    return writeCompressedBitmap(context, bitmap)
  }

  private fun writeCompressedBitmap(context: Context, bitmap: Bitmap): File {
    val outputFile =
        File.createTempFile(
            "meal-compressed-",
            ".jpg",
            File(context.cacheDir, "meal-captures").apply { mkdirs() },
        )

    FileOutputStream(outputFile).use { output ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
    }

    bitmap.recycle()
    return outputFile
  }

  private fun decodeScaledBitmap(context: Context, sourceUri: Uri): Bitmap {
    val options =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
        }

    context.contentResolver.openInputStream(sourceUri).use { stream ->
      BitmapFactory.decodeStream(stream, null, options)
    }

    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1600)
    options.inJustDecodeBounds = false

    return context.contentResolver.openInputStream(sourceUri).use { stream ->
      BitmapFactory.decodeStream(stream, null, options)
    } ?: throw IOException("Unable to decode captured image")
  }

  private fun decodeScaledBitmap(sourceFile: File): Bitmap {
    val options =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFile(sourceFile.absolutePath, this)
        }

    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1600)
    options.inJustDecodeBounds = false

    return BitmapFactory.decodeFile(sourceFile.absolutePath, options)
        ?: throw IOException("Unable to decode captured image")
  }

  private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var largestDimension = max(width, height)

    while (largestDimension / sampleSize > maxDimension) {
      sampleSize *= 2
    }

    return sampleSize.coerceAtLeast(1)
  }
}
