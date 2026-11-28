package com.gamex.devicetester.core

/** Groups individual tests into the cards shown on the full-test progress screen. */
data class TestGroup(val key: String, val title: String, val subtitle: String, val icon: String)

object TestGroups {
    val ordered = listOf(
        TestGroup("display", "Display Test", "Colors, touch, dead pixels", "🖥️"),
        TestGroup("audio", "Audio Test", "Speaker, earpiece, microphone", "🔊"),
        TestGroup("vibration", "Vibration Test", "Haptic motor", "📳"),
        TestGroup("buttons", "Buttons Test", "Volume & power keys", "🔘"),
        TestGroup("sensors", "Sensor Test", "Proximity, accelerometer, gyro", "🧭"),
        TestGroup("camera", "Camera Test", "Front, rear, flash, focus", "📷"),
        TestGroup("fingerprint", "Fingerprint Test", "Biometric sensor", "☝️"),
        TestGroup("face", "Face ID Test", "Face unlock availability", "🙂"),
        TestGroup("network", "Network Test", "SIM, IMEI, Wi-Fi, Bluetooth, GPS", "📶"),
        TestGroup("battery", "Battery Test", "Health, temperature, charging", "🔋"),
        TestGroup("system", "System Test", "Storage, root, bootloader", "🔐"),
    )

    fun groupKey(testId: String): String = when {
        testId.startsWith("camera_") || testId == "flash" -> "camera"
        testId in setOf("display", "touch", "multitouch") -> "display"
        testId.startsWith("speaker_") || testId.startsWith("mic_") || testId == "headset" -> "audio"
        testId == "vibration" -> "vibration"
        testId == "buttons" -> "buttons"
        testId in setOf("proximity", "light", "accel", "gyro", "compass", "barometer", "steps", "all_sensors") -> "sensors"
        testId == "fingerprint" -> "fingerprint"
        testId == "face" -> "face"
        testId in setOf("sim", "imei", "wifi", "bluetooth", "gps", "nfc") -> "network"
        testId == "battery" -> "battery"
        else -> "system"
    }

    fun group(key: String): TestGroup = ordered.firstOrNull { it.key == key } ?: ordered.last()

    /** Sort tests so each group's tests run back-to-back in the group order above. */
    fun sortForRun(items: List<TestItem>): List<TestItem> {
        val order = ordered.map { it.key }
        return items.withIndex()
            .sortedWith(compareBy<IndexedValue<TestItem>>({ order.indexOf(groupKey(it.value.id)) }, { it.index }))
            .map { it.value }
    }
}

/** Shortcut tiles on the home screen. */
data class QuickTile(val title: String, val icon: String, val tintKey: Int, val match: (String) -> Boolean)

object QuickTiles {
    const val BLUE = 0
    const val GREEN = 1
    const val PURPLE = 2
    const val ORANGE = 3
    const val CYAN = 4
    const val PINK = 5
    const val YELLOW = 6

    val quick = listOf(
        QuickTile("Network &\nSIM Check", "📶", BLUE) { it == "sim" },
        QuickTile("Wi-Fi\nCheck", "🌐", GREEN) { it == "wifi" },
        QuickTile("Bluetooth\nCheck", "🔗", BLUE) { it == "bluetooth" },
        QuickTile("Camera\nCheck", "📷", CYAN) { it.startsWith("camera_") },
        QuickTile("Microphone\nCheck", "🎙️", PURPLE) { it.startsWith("mic_") },
        QuickTile("Speaker\nCheck", "📞", ORANGE) { it == "speaker_ear" },
        QuickTile("Loud Speaker\nCheck", "🔊", GREEN) { it == "speaker_loud" },
        QuickTile("Proximity\nCheck", "🤚", BLUE) { it == "proximity" },
        QuickTile("Face ID\nCheck", "🙂", CYAN) { it == "face" },
        QuickTile("Fingerprint\nCheck", "☝️", PINK) { it == "fingerprint" },
        QuickTile("Sensors\nCheck", "☀️", YELLOW) { it in setOf("light", "accel", "gyro", "compass", "barometer", "steps", "all_sensors") },
        QuickTile("Display\nCheck", "📱", PURPLE) { it in setOf("display", "touch", "multitouch") },
        QuickTile("Buttons\nCheck", "🔘", ORANGE) { it == "buttons" },
        QuickTile("Vibration\nCheck", "📳", PINK) { it == "vibration" },
        QuickTile("Headphone\nCheck", "🎧", GREEN) { it == "headset" },
    )

    val advanced = listOf(
        QuickTile("Battery\nHealth", "🔋", GREEN) { it == "battery" },
        QuickTile("Storage\nChecker", "💾", BLUE) { it == "storage" },
        QuickTile("Device\nInfo", "ℹ️", YELLOW) { false },
        QuickTile("IMEI\nCheck", "🛡️", PURPLE) { it == "imei" },
        QuickTile("Security\nCheck", "🔐", CYAN) { it == "security" },
    )
}
