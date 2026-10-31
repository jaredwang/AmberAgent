package app.amber.agent

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner

/**
 * Default runner uses a plain [Application] so lock-safe Room / JSON / ART tests
 * do not start Koin or user jobs.
 *
 * Pass `-e amberFixture s9` to install [ChatDeviceFixtureApplication] instead:
 * isolated files/DB/prefs and Koin, without [AmberAgentApp] cron/bubble/recovery.
 * Needed before any target-package Activity so [ReminderRescheduleReceiver]
 * does not crash with `KoinApplication has not been started`.
 */
class AmberAgentAndroidTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        val fixture = instrumentationArguments()?.getString(ARG_FIXTURE)
        val applicationName = if (fixture == FIXTURE_S9) {
            ChatDeviceFixtureApplication::class.java.name
        } else {
            Application::class.java.name
        }
        Log.i(TAG, "newApplication fixture=${fixture ?: "<none>"} app=$applicationName")
        return super.newApplication(cl, applicationName, context)
    }

    /**
     * AndroidJUnitRunner fills its own arguments in [onCreate], which runs after
     * [newApplication]. Android 17 Instrumentation has no `mArguments` field;
     * ActivityThread bind data still carries `instrumentationArgs`. Xiaomi may
     * rename the bind field, so scan the thread and one nested level.
     */
    private fun instrumentationArguments(): Bundle? {
        scanBundles(this).firstOrNull(::isInstrumentationArgs)?.let { return it }
        return runCatching {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getMethod("currentActivityThread").invoke(null)
            namedBoundApplication(threadClass, thread)
                ?.let { scanBundles(it).firstOrNull(::isInstrumentationArgs) }
                ?: scanBundles(thread).firstOrNull(::isInstrumentationArgs)
                ?: scanNestedBundles(thread).firstOrNull(::isInstrumentationArgs)
        }.onFailure { error ->
            Log.w(TAG, "instrumentation arguments unavailable in newApplication: ${error.message}")
        }.getOrNull()
    }

    private fun namedBoundApplication(threadClass: Class<*>, thread: Any?): Any? {
        val names = listOf("mBoundApplication", "mBoundApp", "boundApplication")
        for (name in names) {
            val value = runCatching {
                threadClass.getDeclaredField(name).apply { isAccessible = true }.get(thread)
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun isInstrumentationArgs(bundle: Bundle): Boolean =
        bundle.containsKey(ARG_FIXTURE) || bundle.containsKey("class") || bundle.containsKey("package")

    private fun scanBundles(target: Any?): List<Bundle> {
        if (target == null) return emptyList()
        val out = mutableListOf<Bundle>()
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (!Bundle::class.java.isAssignableFrom(field.type)) continue
                field.isAccessible = true
                val value = runCatching { field.get(target) as? Bundle }.getOrNull() ?: continue
                Log.i(TAG, "bundle ${type.name}.${field.name} keys=${value.keySet()}")
                out += value
            }
            type = type.superclass
        }
        return out
    }

    private fun scanNestedBundles(target: Any?): List<Bundle> {
        if (target == null) return emptyList()
        val out = mutableListOf<Bundle>()
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (field.type.isPrimitive || field.type == String::class.java) continue
                if (Bundle::class.java.isAssignableFrom(field.type)) continue
                field.isAccessible = true
                val nested = runCatching { field.get(target) }.getOrNull() ?: continue
                if (nested === target) continue
                out += scanBundles(nested)
            }
            type = type.superclass
        }
        return out
    }

    companion object {
        const val ARG_FIXTURE = "amberFixture"
        const val FIXTURE_S9 = "s9"
        private const val TAG = "AmberS9Runner"
    }
}
