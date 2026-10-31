package com.redmioppocamera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Early device-validation build.
 * It enumerates Camera2 JPEG sizes and captures the largest advertised JPEG stream.
 * The selected size is NOT proof of a true 108 MP sensor stream; validate on-device.
 */
class MainActivity : Activity() {
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private lateinit var mode: Spinner
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewRequest: CaptureRequest? = null
    private var cameraId: String? = null
    private var jpegSizes: List<android.util.Size> = emptyList()
    private var selectedSize: android.util.Size? = null
    private lateinit var manager: CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildUi()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            preview.post { openMainCamera() }
        } else requestPermissions(arrayOf(Manifest.permission.CAMERA), 40)
    }

    private fun buildUi() {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101114.toInt())
            setPadding(18, 22, 18, 18)
        }
        val top = TextView(this).apply {
            text = "A U R A   C A M E R A"
            textSize = 18f
            setTextColor(0xFFF5F1EA.toInt())
            gravity = Gravity.CENTER
            letterSpacing = .16f
        }
        preview = TextureView(this)
        status = TextView(this).apply {
            text = "Preparing camera…"
            textSize = 12f
            setTextColor(0xFFD9C9B1.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
        mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("PHOTO", "108MP ULTRA HD", "PORTRAIT (experimental)", "NIGHT (experimental)"))
        }
        val capture = Button(this).apply {
            text = "●"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFB98D63.toInt()))
            setOnClickListener { captureStill() }
        }
        base.addView(top, LinearLayout.LayoutParams(-1, 54))
        base.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        base.addView(status, LinearLayout.LayoutParams(-1, -2))
        base.addView(mode, LinearLayout.LayoutParams(-1, 52))
        base.addView(capture, LinearLayout.LayoutParams(76, 76).apply { gravity = Gravity.CENTER; topMargin = 10 })
        setContentView(base)
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) { openMainCamera() }
            override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
        }
    }

    private fun openMainCamera() {
        if (!preview.isAvailable || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val candidates = manager.cameraIdList.mapNotNull { id ->
                val c = manager.getCameraCharacteristics(id)
                val facing = c.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
                    Triple(id, c, sizes)
                } else null
            }
            val chosen = candidates.maxByOrNull { item ->
                item.third.maxOfOrNull { it.width.toLong() * it.height } ?: 0L
            } ?: run { status.text = "No rear JPEG camera found"; return }
            cameraId = chosen.first
            jpegSizes = chosen.third.sortedByDescending { it.width.toLong() * it.height }
            selectedSize = jpegSizes.firstOrNull()
            val tex = preview.surfaceTexture ?: return
            val size = previewSize()
            tex.setDefaultBufferSize(size.width, size.height)
            val surface = Surface(tex)
            manager.openCamera(chosen.first, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    startPreview(surface)
                    val largest = selectedSize
                    status.text = "Rear camera ready • largest advertised JPEG: ${largest?.width} × ${largest?.height}"
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; status.text = "Camera error: $error" }
            }, null)
        } catch (e: Exception) { status.text = "Camera setup failed: ${e.message}" }
    }

    private fun previewSize(): android.util.Size =
        android.util.Size(1920, 1080)

    private fun startPreview(surface: Surface) {
        val device = camera ?: return
        try {
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }
                    previewRequest = req.build()
                    s.setRepeatingRequest(previewRequest!!, null, null)
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { status.text = "Preview configuration failed" }
            }, null)
        } catch (e: Exception) { status.text = "Preview error: ${e.message}" }
    }

    private fun captureStill() {
        val device = camera ?: return
        val size = selectedSize ?: run { status.text = "No JPEG output size available"; return }
        try {
            reader?.close()
            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).also { imageReader ->
                imageReader.setOnImageAvailableListener({ r ->
                    val image = r.acquireNextImage() ?: return@setOnImageAvailableListener
                    val bytes = ByteArray(image.planes[0].buffer.remaining()).also { image.planes[0].buffer.get(it) }
                    image.close()
                    saveJpeg(bytes)
                }, null)
            }
            val output = reader!!.surface
            device.createCaptureSession(listOf(output), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(stillSession: CameraCaptureSession) {
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(output)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.JPEG_QUALITY, 98.toByte())
                        }.build()
                        stillSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                                status.text = "Captured ${size.width} × ${size.height} • saving…"
                                s.close()
                                val texture = preview.surfaceTexture ?: return
                                startPreview(Surface(texture))
                            }
                        }, null)
                    } catch (e: Exception) { status.text = "Capture failed: ${e.message}" }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { status.text = "Still capture configuration failed" }
            }, null)
        } catch (e: Exception) { status.text = "Capture error: ${e.message}" }
    }

    private fun saveJpeg(bytes: ByteArray) {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "AURA_$stamp.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            runOnUiThread { status.text = "Saved: ${file.name} (app Pictures folder)" }
        } catch (e: Exception) { runOnUiThread { status.text = "Save failed: ${e.message}" } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openMainCamera()
        else status.text = "Camera permission is required"
    }

    override fun onPause() { super.onPause(); session?.close(); session = null; camera?.close(); camera = null }
}
