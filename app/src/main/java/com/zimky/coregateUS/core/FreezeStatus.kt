package com.zimky.coregateUS.core

/** Merge simultaneous read requests; each later refresh still reads current device state. */
object FreezeStatus {
    private var reading = false
    private val callbacks = mutableListOf<(Int, String, String) -> Unit>()

    fun read(done: (Int, String, String) -> Unit) {
        callbacks.add(done)
        if (reading) return
        reading = true
        RootShell.exec("sh /data/adb/modules/CoreGate/service.sh --freeze-status") { code, out, err ->
            val waiting = callbacks.toList()
            callbacks.clear()
            reading = false
            waiting.forEach { it(code, out, err) }
        }
    }
}
