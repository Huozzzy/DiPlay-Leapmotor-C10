package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A local way to adjust the 2024+ Leapmotor adaptation from `adb shell`.
 *
 * Head units differ inside this generation: some report wheel presses only as the factory
 * broadcast, some only as Android media keys, and some as both. Rather than guess once at build
 * time, the setting can be flipped while the car is in front of you:
 *
 * ```
 * adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE \
 *     --es wheel_source factory        # auto | factory | android
 * adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --ez read_vehicle true
 * adb logcat -s DiPlay-Leapmotor24
 * ```
 *
 * Reading the vehicle succeeds or fails entirely against the car's own service, so it is worth
 * triggering by hand before assuming a model does not answer.
 */
class Leapmotor24ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            val source = intent.getStringExtra("wheel_source")
            if (source != null) {
                val applied = Leapmotor24Settings.setWheelSource(context, source)
                android.util.Log.i(
                    TAG,
                    if (applied) "wheel source set to $source" else "wheel source rejected: $source",
                )
            }
            if (intent.getBooleanExtra("read_vehicle", false)) {
                Leapmotor24CarData.attach(context.applicationContext)
                Leapmotor24CarData.read()
            }
        } finally {
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "DiPlay-Leapmotor24"
    }
}
