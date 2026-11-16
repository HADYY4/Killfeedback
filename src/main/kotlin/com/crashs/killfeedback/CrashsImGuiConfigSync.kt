package com.crashs.killfeedback

/** Debounced disk writes for the live ImGui settings. */
object CrashsImGuiConfigSync {

    private const val SAVE_DELAY_MS = 400L
    private var dirty = false
    private var lastChangeAt = 0L

    fun touch() {
        dirty = true
        lastChangeAt = System.currentTimeMillis()
    }

    fun tick() {
        if (!dirty) return
        if (System.currentTimeMillis() - lastChangeAt >= SAVE_DELAY_MS) {
            flush()
        }
    }

    fun flush() {
        if (!dirty) return
        KillFeedbackConfigManager.save()
        dirty = false
    }
}
