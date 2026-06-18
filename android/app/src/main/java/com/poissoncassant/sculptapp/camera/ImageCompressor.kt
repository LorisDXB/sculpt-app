package com.poissoncassant.sculptapp.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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

  fun decodePreviewBitmap(sourceFile: File, maxDimension: Int): Bitmap {
    val options =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFile(sourceFile.absolutePath, this)
        }

    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxDimension)
    options.inJustDecodeBounds = false

    val bitmap =
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            ?: throw IOException("Unable to decode captured image")
    return applyExifOrientation(bitmap, readExifOrientation(sourceFile))
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

    val bitmap = context.contentResolver.openInputStream(sourceUri).use { stream ->
      BitmapFactory.decodeStream(stream, null, options)
    } ?: throw IOException("Unable to decode captured image")
    return applyExifOrientation(bitmap, readExifOrientation(context, sourceUri))
  }

  private fun decodeScaledBitmap(sourceFile: File): Bitmap {
    val options =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFile(sourceFile.absolutePath, this)
        }

    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1600)
    options.inJustDecodeBounds = false

    val bitmap =
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            ?: throw IOException("Unable to decode captured image")
    return applyExifOrientation(bitmap, readExifOrientation(sourceFile))
  }

  private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var largestDimension = max(width, height)

    while (largestDimension / sampleSize > maxDimension) {
      sampleSize *= 2
    }

    return sampleSize.coerceAtLeast(1)
  }

  private fun readExifOrientation(context: Context, sourceUri: Uri): Int =
      context.contentResolver.openInputStream(sourceUri).use { stream ->
        if (stream == null) {
          ExifInterface.ORIENTATION_UNDEFINED
        } else {
          ExifInterface(stream)
              .getAttributeInt(
                  ExifInterface.TAG_ORIENTATION,
                  ExifInterface.ORIENTATION_UNDEFINED,
              )
        }
      }

  private fun readExifOrientation(sourceFile: File): Int =
      ExifInterface(sourceFile.absolutePath)
          .getAttributeInt(
              ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_UNDEFINED,
          )

  private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.preScale(-1f, 1f)
        matrix.postRotate(270f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.preScale(-1f, 1f)
        matrix.postRotate(90f)
      }
      else -> return bitmap
    }

    val rotatedBitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotatedBitmap != bitmap) {
      bitmap.recycle()
    }
    return rotatedBitmap
  }
}
