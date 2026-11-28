package com.gamex.devicetester

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gamex.devicetester.core.DeviceInfo
import com.gamex.devicetester.ui.KeyValueList
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap

/** Full hardware / software specification of the phone. */
class DeviceInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Palette.BG) }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        col.addView(label("Device information", 24f, Palette.TEXT, true), lpMatchWrap(bottom = dp(8)))
        val info = DeviceInfo.collect(this)
        for ((section, list) in info) {
            col.addView(label(section.uppercase(), 12f, Palette.ACCENT, true), lpMatchWrap(top = dp(12), bottom = dp(6)))
            val c = card()
            val kv = KeyValueList(this)
            for ((k, v) in list) kv.set(k, v)
            c.addView(kv)
            col.addView(c, lpMatchWrap())
        }
        col.addView(actionButton("📋 Copy all") {
            val text = info.entries.joinToString("\n\n") { (s, l) -> s + "\n" + l.joinToString("\n") { "${it.first}: ${it.second}" } }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Device info", text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }, lpMatchWrap(top = dp(16)))
    }
}
