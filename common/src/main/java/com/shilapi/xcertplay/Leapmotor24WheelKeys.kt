package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController

/**
 * Preferences for the 2024+ Leapmotor head unit.
 *
 * The only setting DiPlay needs here is where steering-wheel media keys come from, because on this
 * generation a single physical press can arrive twice: once as the head unit's own broadcast and
 * once as an ordinary Android media key event. See [Leapmotor24WheelKeys].
 */
internal object Leapmotor24Settings {
    private const val PREFS = "xcertplay_airplay"
    const val KEY_WHEEL_SOURCE = "leapmotor24_wheel_source"

    /** Accepted values for [KEY_WHEEL_SOURCE]. */
    const val SOURCE_AUTO = "auto"
    const val SOURCE_FACTORY = "factory"
    const val SOURCE_ANDROID = "android"

    fun wheelSource(context: Context): String =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
            .getOrNull()
            ?.getString(KEY_WHEEL_SOURCE, SOURCE_AUTO)
            ?: SOURCE_AUTO

    fun setWheelSource(context: Context, source: String): Boolean {
        if (source != SOURCE_AUTO && source != SOURCE_FACTORY && source != SOURCE_ANDROID) return false
        val editor = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit() }.getOrNull()
            ?: return false
        return runCatching {
            editor.putString(KEY_WHEEL_SOURCE, source).apply()
            true
        }.getOrDefault(false)
    }
}

/**
 * Steering-wheel media keys on Leapmotor's 2024 and later head units (C10 onwards).
 *
 * Unlike the 2021–2023 firmware — see [LeapmotorMediaKeys] — this generation publishes one single
 * broadcast with one integer payload, and nothing else: previous, next and play/pause. The volume
 * and phone keys belong to the car and are never taken over.
 *
 * Another difference matters more in practice: on these cars the same physical press also surfaces
 * as an ordinary Android media key event. A head unit that does both would otherwise skip two
 * tracks, or pause and immediately resume. So this generation routes every press through one
 * de-duplication window shared by both paths, see [claim].
 */
internal object Leapmotor24WheelKeys {
    private const val TAG = "DiPlay-Leapmotor24"

    /** The head unit's own steering-wheel media broadcast. */
    const val ACTION_MEDIA = "com.leapmotor.steerwheelcontrol.media"
    private const val EXTRA_ACTION = "action"

    /**
     * Both streams of one press can be far apart: the head unit's own path round-trips through the
     * car, while the key event arrives with the input stack. This is wider than the 250 ms used for
     * the 2021–2023 broadcast-only case for that reason.
     */
    private const val DUPLICATE_WINDOW_MILLIS = 350L

    /** Which route a press reached DiPlay through. */
    enum class Route { FACTORY, ANDROID }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var receiver: BroadcastReceiver? = null
    @Volatile private var controller: CarPlayController? = null
    @Volatile var onDiagnostic: ((String) -> Unit)? = null
    private var lastIndex = 0
    private var lastUptimeMillis = 0L

    @Synchronized
    fun attach(context: Context, next: CarPlayController) {
        appContext = context.applicationContext
        controller = next
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pending = goAsync()
                mainHandler.post {
                    try {
                        handle(intent)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
        // EXPORTED, unlike the 2021–2023 set: this broadcast comes from the car's own control
        // service, which is not always a privileged UID on these builds.
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                created,
                IntentFilter(ACTION_MEDIA),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        receiver = created.takeIf { registered }
        report("wheel keys listening on $ACTION_MEDIA registered=$registered source=${Leapmotor24Settings.wheelSource(context)}")
    }

    @Synchronized
    fun detach(expected: CarPlayController?) {
        if (expected != null && controller !== expected && controller != null) return
        val context = appContext
        val existing = receiver
        if (context != null && existing != null) runCatching { context.unregisterReceiver(existing) }
        receiver = null
        appContext = null
        controller = null
        report("wheel keys detached")
    }

    /** The single heading unit action codes; anything else on this channel belongs to the car. */
    fun forFactoryAction(action: Int): Int? = when (action) {
        1 -> CarPlayMediaButton.PREVIOUS
        4 -> CarPlayMediaButton.NEXT
        7 -> CarPlayMediaButton.PLAY_PAUSE
        else -> null
    }

    private fun currentSource(fallback: Context?): String =
        (fallback ?: appContext)?.let(Leapmotor24Settings::wheelSource) ?: Leapmotor24Settings.SOURCE_AUTO

    /**
     * Whether [index] should be acted on, having arrived via [route].
     *
     * This is what collapses the two streams of one physical press into a single action, and it is
     * also where the "factory only" and "Android key only" overrides are applied for cars that turn
     * out to deliver only one of them.
     */
    @Synchronized
    fun claim(route: Route, index: Int, context: Context? = null): Boolean {
        when (currentSource(context)) {
            Leapmotor24Settings.SOURCE_FACTORY -> if (route != Route.FACTORY) {
                report("key suppressed: factory source only route=$route")
                return false
            }
            Leapmotor24Settings.SOURCE_ANDROID -> if (route != Route.ANDROID) {
                report("key suppressed: android source only route=$route")
                return false
            }
        }
        val now = SystemClock.uptimeMillis()
        if (index == lastIndex && now - lastUptimeMillis < DUPLICATE_WINDOW_MILLIS) {
            report("key duplicate suppressed route=$route index=$index")
            return false
        }
        lastIndex = index
        lastUptimeMillis = now
        return true
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != ACTION_MEDIA) return
        val raw = runCatching { intent.getIntExtra(EXTRA_ACTION, -1) }.getOrDefault(-1)
        val button = forFactoryAction(raw)
        if (button == null) {
            report("factory key ignored action=$raw")
            return
        }
        if (!claim(Route.FACTORY, button)) return
        dispatch(button, "factory:$raw")
    }

    /** Sends [button] to CarPlay, or to the car's own video player while that owns the screen. */
    fun dispatch(button: Int, source: String): Boolean {
        if (CarPlayVideo.onMediaKey(button)) {
            report("key $source -> video player $button")
            return true
        }
        val active = controller
        if (active == null) {
            report("key ignored (no CarPlay session) $source")
            return false
        }
        val sent = active.sendMediaButton(button)
        report("key $source -> CarPlay $button sent=$sent")
        return sent
    }

    /** Called from the Android key paths so they do not double up with the factory broadcast. */
    fun claimAndroidKey(index: Int): Boolean = claim(Route.ANDROID, index)

    private fun report(message: String) {
        android.util.Log.i(TAG, message)
        runCatching { onDiagnostic?.invoke(message) }
    }
}
