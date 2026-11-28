package com.gamex.devicetester.tests

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.core.CameraSpecs
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.dpf
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.gamex.devicetester.ui.roundedBg
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Live preview, tap-to-focus, zoom (lens switching), torch and photo capture for one camera. */
class CameraTestActivity : BaseTestActivity() {
    companion object {
        const val EXTRA_CAMERA_ID = "camera_id"
    }

    private lateinit var cameraId: String
    private lateinit var previewView: PreviewView
    private lateinit var thumb: ImageView
    private lateinit var zoomLabel: TextView
    private lateinit var torchBtn: MaterialButton
    private lateinit var captureBtn: MaterialButton
    private lateinit var zoomBar: SeekBar
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var torchOn = false
    private var lastPhoto: Bitmap? = null
    private var photoInfo: String? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun currentNote(): String? = photoInfo

    override fun onCreateTest(savedInstanceState: Bundle?) {
        cameraId = intent.getStringExtra(EXTRA_CAMERA_ID) ?: "0"
        setInstruction("Check the preview is sharp with natural colours and no spots. Tap to focus. Slide zoom to test other lenses. Take a photo, then Pass.")

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(label(CameraSpecs.details(this, cameraId), 11f, Palette.MUTED), lpMatchWrap(bottom = dp(6)))

        val frame = FrameLayout(this).apply { background = roundedBg(Color.BLACK, dpf(14f)) }
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        frame.addView(previewView, FrameLayout.LayoutParams(MATCH, MATCH))
        thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg(Palette.CARD, dpf(8f), Color.WHITE, dp(2))
            visibility = android.view.View.GONE
            setOnClickListener { showFullPhoto() }
        }
        frame.addView(thumb, FrameLayout.LayoutParams(dp(84), dp(112), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8)
            marginEnd = dp(8)
        })
        zoomLabel = label("1.0x", 14f, Color.WHITE, true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBg(Color.argb(150, 0, 0, 0), dpf(10f))
        }
        frame.addView(zoomLabel, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(8)
        })
        col.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f))

        zoomBar = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) camera?.cameraControl?.setLinearZoom(p / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        col.addView(zoomBar, lpMatchWrap(top = dp(6)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        torchBtn = actionButton("🔦 Torch", Palette.CARD_ALT) { toggleTorch() }
        captureBtn = actionButton("📸 Take photo") { takePhoto() }
        row.addView(torchBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        row.addView(captureBtn, LinearLayout.LayoutParams(0, WRAP, 1.6f))
        col.addView(row, lpMatchWrap(top = dp(4)))
        torchBtn.isEnabled = false
        captureBtn.isEnabled = false

        withPermissions(Manifest.permission.CAMERA) { ok ->
            if (ok) startCamera() else setStatus("Camera permission denied", Palette.FAIL)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        setStatus("Opening camera…", Palette.MUTED)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                setStatus("Camera service error: ${e.message}", Palette.FAIL)
                return@addListener
            }
            val selector = CameraSelector.Builder()
                .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == cameraId } }
                .build()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                val cam = provider.bindToLifecycle(this, selector, preview, capture)
                camera = cam
                imageCapture = capture
                captureBtn.isEnabled = true
                torchBtn.isEnabled = cam.cameraInfo.hasFlashUnit()
                if (!cam.cameraInfo.hasFlashUnit()) torchBtn.text = "No flash"
                cam.cameraInfo.zoomState.observe(this) { zs ->
                    zoomLabel.text = String.format("%.1fx  (%.1f–%.1fx)", zs.zoomRatio, zs.minZoomRatio, zs.maxZoomRatio)
                }
                previewView.setOnTouchListener { v, ev ->
                    if (ev.action == MotionEvent.ACTION_UP) {
                        val pt = previewView.meteringPointFactory.createPoint(ev.x, ev.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                        setStatus("Focusing…", Palette.MUTED)
                        v.performClick()
                    }
                    true
                }
                setStatus("Camera ready — take a photo", Palette.TEXT)
            } catch (e: Exception) {
                setStatus("Could not open camera $cameraId: ${e.message}", Palette.FAIL)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        torchBtn.text = if (torchOn) "🔦 Torch ON" else "🔦 Torch"
    }

    private fun takePhoto() {
        val cap = imageCapture ?: return
        captureBtn.isEnabled = false
        setStatus("Capturing…", Palette.MUTED)
        cap.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val w = image.width
                val h = image.height
                val bmp = try {
                    decode(image)
                } catch (e: Throwable) {
                    null
                } finally {
                    image.close()
                }
                runOnUiThread {
                    captureBtn.isEnabled = true
                    if (bmp == null) {
                        setStatus("Photo captured but could not be decoded", Palette.SKIP)
                        return@runOnUiThread
                    }
                    lastPhoto = bmp
                    thumb.setImageBitmap(bmp)
                    thumb.visibility = android.view.View.VISIBLE
                    val mp = w.toLong() * h / 1_000_000.0
                    photoInfo = String.format("Photo %d×%d (%.1f MP) captured", w, h, mp)
                    setStatus("✓ $photoInfo — tap thumbnail to inspect, then Pass", Palette.PASS)
                }
            }

            override fun onError(exc: ImageCaptureException) {
                runOnUiThread {
                    captureBtn.isEnabled = true
                    setStatus("Capture failed: ${exc.message}", Palette.FAIL)
                }
            }
        })
    }

    private fun decode(image: ImageProxy): Bitmap? {
        if (image.format != ImageFormat.JPEG) return null
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        val opts = BitmapFactory.Options()
        var sample = 1
        while (image.width / (sample * 2) >= 1000) sample *= 2
        opts.inSampleSize = sample
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val rot = image.imageInfo.rotationDegrees
        if (rot == 0) return raw
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    private fun showFullPhoto() {
        val bmp = lastPhoto ?: return
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { d.dismiss() }
        }
        d.setContentView(iv, ViewGroup.LayoutParams(MATCH, MATCH))
        d.show()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}

/** Torch blinks a random number of times; tester counts them. */
class FlashTestActivity : BaseTestActivity() {
    private lateinit var cm: CameraManager
    private var flashId: String? = null
    private var manualOn = false

    override fun onCreateTest(savedInstanceState: Bundle?) {
        cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        flashId = try {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (e: Exception) {
            null
        }
        setInstruction("Look at the LED flash on the back. It will blink a random number of times — count the flashes.")
        val col = scrollColumn()
        if (flashId == null) {
            setStatus("No flash unit reported by the camera system", Palette.FAIL)
            return
        }
        addCountQuiz(col, "flashes", "▶ Start blinking") { n, done ->
            for (i in 0 until n) {
                handler.postDelayed({ torch(true) }, 400L + i * 750L)
                handler.postDelayed({ torch(false) }, 400L + i * 750L + 380L)
            }
            handler.postDelayed({ done() }, 400L + n * 750L + 200L)
        }
        col.addView(actionButton("🔦 Torch on / off", Palette.CARD_ALT) {
            manualOn = !manualOn
            torch(manualOn)
        }, lpMatchWrap(top = dp(8)))
    }

    private fun torch(on: Boolean) {
        val id = flashId ?: return
        try {
            cm.setTorchMode(id, on)
        } catch (e: Exception) {
            setStatus("Torch error: ${e.message}", Palette.FAIL)
        }
    }

    override fun onDestroy() {
        torch(false)
        super.onDestroy()
    }
}
