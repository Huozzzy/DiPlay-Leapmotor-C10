package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Vehicle data from Leapmotor's 2024 and later head units (C10 onwards), read over the car's own
 * service rather than from CAN broadcasts.
 *
 * The 2021–2023 cars push gear and CAN data to whoever listens (see [LeapmotorGearMonitor]); this
 * generation exposes a bound Binder pool instead, and DiPlay has to ask. It is reached through
 * `com.leapmotor.carcontrol`'s binder pool service: the pool hands out an endpoint Binder, and the
 * endpoint answers property reads.
 *
 * **This is a read-only diagnostic.** Nothing here decides DiPlay's gear, asserts the car is parked,
 * or otherwise drives behaviour. What the car reports may lag, may be missing on some models, and
 * is deliberately not treated as authority — it exists so an owner can see whether the car answers
 * at all, and with what.
 */
internal object Leapmotor24CarData {
    private const val TAG = "DiPlay-Leapmotor24"

    private const val SERVICE_ACTION = "com.leapmotor.carcontrol.services.BINDER_POOL"
    private const val SERVICE_PACKAGE = "com.leapmotor.carcontrol"
    private const val SERVICE_CLASS = "com.leapmotor.carcontrol.services.BinderPoolService"

    /** The pool itself: transact 1 returns one of its endpoints as a strong binder. */
    private const val POOL_DESCRIPTOR = "com.leapmotor.carcontrol.IBinderPool"
    private const val POOL_TRANSACT_QUERY_ENDPOINT = 1

    private const val DESCRIPTOR_CAR_CONTROL = "com.leapmotor.carcontrol.ICarControl"
    private const val DESCRIPTOR_CAR_CONTROL_EXTERNAL = "com.leapmotor.carcontrol.ICarControlExternal"

    /** Endpoint ids inside the pool. Both are asked; only one usually answers on a given model. */
    private val ENDPOINTS = listOf(1, 2)

    /**
     * The vehicle properties DiPlay reads: speed and gear, in some order. Which is which is not
     * known from the interface alone — only the two ids the car accepts — so both are reported by
     * id and left unlabelled rather than guessed at.
     */
    private val PROPERTIES = listOf(0xFBE, 0x145B)

    private const val BIND_TIMEOUT_MILLIS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var connection: ServiceConnection? = null
    @Volatile var lastSnapshot: String = "unknown"
        private set
    @Volatile var onDiagnostic: ((String) -> Unit)? = null

    @Synchronized
    fun attach(context: Context) {
        appContext = context.applicationContext
        read()
    }

    @Synchronized
    fun detach() {
        unbindQuietly()
        appContext = null
    }

    /** Takes one snapshot of the two properties, unbinding again once answered. */
    fun read() {
        mainHandler.post { readLocked() }
    }

    @Synchronized
    private fun readLocked() {
        val context = appContext ?: return
        val intent = Intent(SERVICE_ACTION).setComponent(ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
        val created = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                mainHandler.post {
                    if (connection !== this) return@post
                    if (service == null) {
                        snapshot("no binder supplied")
                    } else {
                        snapshot(readThroughPool(service).joinToString(" | "))
                    }
                    unbindQuietly()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                report("car control service disconnected")
                connection = null
            }

            override fun onBindingDied(name: ComponentName?) {
                report("car control binding died")
                unbindQuietly()
            }

            override fun onNullBinding(name: ComponentName?) {
                snapshot("car control bound without a binder")
                unbindQuietly()
            }
        }
        connection = created
        val bound = runCatching {
            context.bindService(intent, created, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }.getOrDefault(false)
        if (!bound) {
            report("cannot bind $SERVICE_PACKAGE: the device does not provide it, or it is not permitted")
            snapshot("car control unavailable")
            connection = null
            return
        }
        mainHandler.postDelayed({
            if (connection === created) {
                report("car control bind timed out after $BIND_TIMEOUT_MILLIS ms")
                snapshot("car control bind timed out")
                unbindQuietly()
            }
        }, BIND_TIMEOUT_MILLIS)
    }

    /** Reads every property through every endpoint that accepts them. */
    private fun readThroughPool(pool: IBinder): List<String> {
        if (pool.interfaceDescriptor != POOL_DESCRIPTOR) {
            return listOf("pool descriptor mismatch: ${runCatching { pool.interfaceDescriptor }.getOrNull()}")
        }
        val lines = ArrayList<String>()
        for (endpoint in ENDPOINTS) {
            val binder = queryEndpoint(pool, endpoint)
            if (binder == null) {
                lines += "endpoint $endpoint: absent"
                continue
            }
            val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
            lines += "endpoint $endpoint: $descriptor"
            if (descriptor != DESCRIPTOR_CAR_CONTROL && descriptor != DESCRIPTOR_CAR_CONTROL_EXTERNAL) {
                lines += "endpoint $endpoint: unknown descriptor, not read"
                continue
            }
            for (property in PROPERTIES) {
                val raw = readProperty(binder, descriptor, property)
                val value = numericOf(raw)
                lines += when {
                    value == null -> "$property: no usable value${raw?.let { " (raw=$it)" } ?: ""}"
                    else -> "$property: $value (raw=$raw)"
                }
            }
        }
        return lines
    }

    /** The pool's own query: ask for endpoint [endpoint] and read the returned binder. */
    private fun queryEndpoint(pool: IBinder, endpoint: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(POOL_DESCRIPTOR)
            data.writeInt(endpoint)
            if (!pool.transact(POOL_TRANSACT_QUERY_ENDPOINT, data, reply, 0)) {
                report("pool refused the endpoint query for $endpoint")
                null
            } else {
                reply.readException()
                reply.readStrongBinder()
            }
        } catch (t: Throwable) {
            report("endpoint $endpoint query failed: ${t.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Reads [property] from [binder]. The endpoint's transact code depends on which interface it
     * answers to, and the reply is a string that either holds a bare number or a small JSON object.
     */
    private fun readProperty(binder: IBinder, descriptor: String, property: Int): String? {
        val code = when (descriptor) {
            DESCRIPTOR_CAR_CONTROL -> 2
            DESCRIPTOR_CAR_CONTROL_EXTERNAL -> 1
            else -> return null
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.writeInt(property)
            if (!binder.transact(code, data, reply, 0)) {
                report("endpoint refused getCanData code=$code property=$property")
                null
            } else {
                reply.readException()
                reply.readString()
            }
        } catch (t: Throwable) {
            report("property $property read failed: ${t.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** The car answers with `{"value": …}`, `{"data": …}` or a plain number; all three occur. */
    fun numericOf(raw: String?): BigDecimal? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 1024 } ?: return null
        return runCatching {
            if (text.startsWith("{")) {
                val root = JSONObject(text)
                val direct = root.opt("value") ?: root.opt("data")
                BigDecimal(direct?.toString())
            } else {
                BigDecimal(text)
            }
        }.getOrNull()
    }

    private fun snapshot(text: String) {
        lastSnapshot = text
        report("vehicle snapshot: $text — for this read only, not used as gear or parking authority")
    }

    private fun unbindQuietly() {
        val context = appContext
        val existing = connection
        connection = null
        if (context != null && existing != null) runCatching { context.unbindService(existing) }
    }

    private fun report(message: String) {
        android.util.Log.i(TAG, message)
        runCatching { onDiagnostic?.invoke(message) }
    }
}
