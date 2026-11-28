package com.gamex.devicetester

import android.app.Application
import com.gamex.devicetester.ui.ThemeManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(ThemeManager.mode(this))
    }
}
