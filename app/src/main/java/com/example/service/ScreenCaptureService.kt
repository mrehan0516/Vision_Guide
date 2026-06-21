package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureService : Service() {

    companion object {
        var isRunning = false
        private var mediaProjection: MediaProjection? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var imageReader: ImageReader? = null
        private var mediaProjectionManager: MediaProjectionManager? = null

        fun setMediaProjection(manager: MediaProjectionManager, projection: MediaProjection) {
            mediaProjectionManager = manager
            mediaProjection = projection
        }

        fun stopCapture() {
            mediaProjection?.stop()
            mediaProjection = null
        }

        suspend fun captureSnapshot(context: Context): String? = suspendCancellableCoroutine { continuation ->
            val projection = mediaProjection
            if (projection == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels / 2
            val height = metrics.heightPixels / 2
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image: Image? = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val planes = image.planes
                            val buffer: ByteBuffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            
                            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                            
                            val outputStream = ByteArrayOutputStream()
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                            val base64String = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                            
                            if (continuation.isActive) {
                                continuation.resume(base64String)
                            }
                        } finally {
                            image.close()
                        }
                    } else {
                         if (continuation.isActive) {
                             continuation.resume(null)
                         }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                } finally {
                    imageReader?.setOnImageAvailableListener(null, null)
                    virtualDisplay?.release()
                    imageReader?.close()
                }
            }, null)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "ScreenCaptureChannel")
            .setContentTitle("Screen Capture Active")
            .setContentText("VisionPilot is ready to capture screen context.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopCapture()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ScreenCaptureChannel",
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
