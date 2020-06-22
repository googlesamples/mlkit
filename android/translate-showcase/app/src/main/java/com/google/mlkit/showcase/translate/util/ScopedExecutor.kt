package com.google.mlkit.showcase.translate.util

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ScopedExecutor(private val executor: Executor) : Executor {

    private val isShutdown = AtomicBoolean()

    fun shutdown() {
        isShutdown.set(true)
    }

    override fun execute(command: Runnable) {
        executor.execute {
            if (!isShutdown.get()) command.run()
        }
    }
}