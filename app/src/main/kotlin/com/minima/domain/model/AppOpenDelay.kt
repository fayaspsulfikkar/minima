package com.minima.domain.model

/**
 * Supported intentional delay durations before opening an application in MINIMA.
 *
 * @param seconds Duration in seconds to wait before launching.
 * @param label Minimal text representation for display in settings.
 */
enum class AppOpenDelay(val seconds: Int, val label: String) {
    OFF(0, "off"),
    THREE_SECONDS(3, "3s"),
    FIVE_SECONDS(5, "5s");

    companion object {
        /**
         * Resolves an [AppOpenDelay] from an integer seconds value.
         * Falls back safely to [OFF] if an unrecognized value is encountered.
         */
        fun fromSeconds(seconds: Int): AppOpenDelay =
            entries.find { it.seconds == seconds } ?: OFF
    }
}

