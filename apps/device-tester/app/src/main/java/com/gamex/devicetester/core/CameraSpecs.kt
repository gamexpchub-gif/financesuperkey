package com.gamex.devicetester.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

object CameraSpecs {

    private fun chars(c: Context, id: String): CameraCharacteristics? = try {
        (c.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(id)
    } catch (e: Exception) {
        null
    }

    private fun facingName(ch: CameraCharacteristics): String = when (ch.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        else -> "External"
    }

    private fun megapixels(ch: CameraCharacteristics): Double {
        val px = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return 0.0
        return px.width.toLong() * px.height / 1_000_000.0
    }

    fun oneLine(c: Context, id: String): String {
        val ch = chars(c, id) ?: return "#$id"
        return String.format("#%s %s %.1f MP", id, facingName(ch), megapixels(ch))
    }

    fun details(c: Context, id: String): String {
        val ch = chars(c, id) ?: return "Camera ID $id"
        val parts = mutableListOf<String>()
        parts += "${facingName(ch)} • ID $id"
        val px = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        if (px != null) parts += String.format("%.1f MP (%d×%d)", megapixels(ch), px.width, px.height)
        ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.let { f ->
            if (f.isNotEmpty()) parts += f.joinToString("/") { String.format("%.2f", it) } + " mm"
        }
        ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.let { a ->
            if (a.isNotEmpty()) parts += a.joinToString("/") { String.format("f/%.1f", it) }
        }
        val ois = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
        if (ois) parts += "OIS"
        val af = (ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.size ?: 0) > 1
        parts += if (af) "Autofocus" else "Fixed focus"
        if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) parts += "Flash"
        if (Build.VERSION.SDK_INT >= 28) {
            val phys = ch.physicalCameraIds
            if (phys.isNotEmpty()) parts += "Multi-lens: ${phys.size} physical (${phys.joinToString(",")})"
        }
        return parts.joinToString(" • ")
    }
}
