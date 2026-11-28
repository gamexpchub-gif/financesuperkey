package com.gamex.devicetester.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

enum class TestStatus { UNTESTED, PASS, FAIL, SKIP }

data class TestItem(
    val id: String,
    val title: String,
    val category: String,
    val icon: String,
    val activity: Class<out Activity>,
    val extras: Bundle = Bundle(),
) {
    fun intent(context: Context, step: String? = null): Intent =
        Intent(context, activity).apply {
            putExtras(this@TestItem.extras)
            putExtra(BaseTestActivity.EXTRA_TEST_ID, id)
            putExtra(BaseTestActivity.EXTRA_TITLE, title)
            if (step != null) putExtra(BaseTestActivity.EXTRA_STEP, step)
        }
}

/** Persists the result of every test so the dashboard and report survive restarts. */
object ResultStore {
    private const val PREFS = "test_results"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun status(c: Context, id: String): TestStatus {
        val raw = prefs(c).getString("s_$id", null) ?: return TestStatus.UNTESTED
        return try {
            TestStatus.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            TestStatus.UNTESTED
        }
    }

    fun note(c: Context, id: String): String? = prefs(c).getString("n_$id", null)

    fun time(c: Context, id: String): Long = prefs(c).getLong("t_$id", 0L)

    fun save(c: Context, id: String, status: TestStatus, note: String?) {
        prefs(c).edit()
            .putString("s_$id", status.name)
            .putString("n_$id", note)
            .putLong("t_$id", System.currentTimeMillis())
            .apply()
    }

    fun clear(c: Context) {
        prefs(c).edit().clear().apply()
    }
}
