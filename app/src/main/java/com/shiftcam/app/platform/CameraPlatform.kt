package com.shiftcam.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX 绑定与拍照（P7）。 */
object CameraPlatform {

    suspend fun provider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { if (cont.isActive) cont.resume(it) }
                        .onFailure { if (cont.isActive) cont.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    suspend fun takePicture(
        context: Context,
        imageCapture: ImageCapture,
        targetFile: File,
    ): File = suspendCancellableCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(targetFile).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(targetFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            },
        )
    }

    suspend fun decodeRotated(file: File): Bitmap = withContext(Dispatchers.IO) {
        val raw = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("原图解码失败")
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            raw
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }
    }
}

/**
 * 成片保存：写 App 专属 Pictures 目录，MediaScanner 扫描进相册，
 * 用 FileProvider 暴露 Uri（不申请 WRITE_EXTERNAL_STORAGE，INV-10 权限清单）。
 */
object PhotoStorage {

    fun saveWatermarked(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "WM_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun originalFile(context: Context): File {
        val dir = File(context.filesDir, "originals")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "RAW_${System.currentTimeMillis()}.jpg")
    }

    fun cacheFile(context: Context): File {
        val dir = File(context.cacheDir, "captures")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "CAP_${System.currentTimeMillis()}.jpg")
    }
}
