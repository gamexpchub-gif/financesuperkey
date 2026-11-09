package com.financesuperkey.dpc

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Periodically checks in with the server: heartbeat, fetch the current policy,
 * apply it, and acknowledge. The server is the single source of truth; this
 * worker only carries out what the server already decided.
 */
class PolicyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val store = PolicyStore(applicationContext)
        if (store.provisioningToken == null) return Result.success()

        val api = ApiClient(store)
        val controller = PolicyController(applicationContext)

        // Heartbeat with basic integrity signals (used for admin review, never to
        // auto-escalate restriction).
        val integrity = JSONObject()
            .put("sdk", Build.VERSION.SDK_INT)
            .put("deviceOwner", controller.isDeviceOwner())
        api.sendHeartbeat(integrity)

        val policy = api.fetchPolicy() ?: return Result.retry()
        controller.apply(policy)
        api.ackPolicy(policy.serverDecisionId)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "fsk_policy_periodic"
        private const val ONESHOT = "fsk_policy_oneshot"

        /** Schedules the recurring check-in (minimum 15-minute interval). */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = PeriodicWorkRequestBuilder<PolicyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, work
            )
        }

        /** Fires an immediate one-off check (e.g. when the app opens). */
        fun runNow(context: Context) {
            val work = OneTimeWorkRequestBuilder<PolicyWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT, ExistingWorkPolicy.REPLACE, work
            )
        }
    }
}
