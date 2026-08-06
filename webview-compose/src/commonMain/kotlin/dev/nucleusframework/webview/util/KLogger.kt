package dev.nucleusframework.webview.util

/**
 * Lightweight logger used by the API layer.
 *
 * Keep logging simple and dependency-free across platforms.
 */
interface KLogger {
    /**
     * Sets the minimum severity level for this logger.
     * Only logs with severity greater than or equal to this level will be output.
     *
     * @param severity The minimum log severity level
     */
    fun setMinSeverity(severity: KLogSeverity)

    /**
     * Convenience method to output a Debug level log.
     *
     * @param tag Optional log tag
     * @param msg Lambda expression that returns the log message
     */
    fun d(tag: String? = null, msg: () -> String) = log(KLogSeverity.Debug, tag, null, msg)

    /**
     * Convenience method to output an Info level log.
     *
     * @param tag Optional log tag
     * @param msg Lambda expression that returns the log message
     */
    fun i(tag: String? = null, msg: () -> String) = log(KLogSeverity.Info, tag, null, msg)

    /**
     * Convenience method to output a Warn level log.
     *
     * @param tag Optional log tag
     * @param msg Lambda expression that returns the log message
     */
    fun w(tag: String? = null, msg: () -> String) = log(KLogSeverity.Warn, tag, null, msg)

    /**
     * Convenience method to output an Error level log.
     *
     * @param t Optional throwable object
     * @param tag Optional log tag
     * @param msg Lambda expression that returns the log message
     */
    fun e(t: Throwable? = null, tag: String? = null, msg: () -> String) = log(KLogSeverity.Error, tag, t, msg)

    /**
     * Outputs a log.
     *
     * @param severity The log severity level
     * @param tag Optional log tag
     * @param t Optional throwable object
     * @param msg Lambda expression that returns the log message
     */
    fun log(severity: KLogSeverity, tag: String?, t: Throwable?, msg: () -> String)


    /**
     * Global logger object.
     *
     * As a global logger, all logging throughout the application will ultimately call
     * the companion object's logging methods. These methods will forward logs to all
     * added loggers.
     *
     * Note: The companion object's [setMinSeverity] will set the minimum output level
     * for all registered loggers.
     */
    companion object : KLogger {
        private val loggers = mutableListOf<KLogger>(DefaultKLogger)

        /**
         * Adds a custom logger to the global logger manager.
         *
         * Once added, all logs output through the companion object will be forwarded to this logger.
         * Multiple custom loggers can be added via this method to achieve multi-target logging
         * (e.g., file, remote server, etc.).
         *
         * @param logger The logger instance to add
         */
        fun addLogger(logger: KLogger) {
            loggers.add(logger)
        }

        /**
         * Removes the specified logger from the global logger manager.
         *
         * After removal, this logger will no longer receive any log messages.
         * Note: The default [DefaultKLogger] cannot be removed.
         *
         * @param logger The logger instance to remove
         */
        fun removeLogger(logger: KLogger) {
            loggers.remove(logger)
        }

        override fun setMinSeverity(severity: KLogSeverity) {
            for (i in loggers) {
                i.setMinSeverity(severity)
            }
        }

        override fun log(
            severity: KLogSeverity,
            tag: String?,
            t: Throwable?,
            msg: () -> String
        ) = loggers.forEach {
            it.log(severity, tag, t, msg)
        }

    }
}

internal object DefaultKLogger : KLogger {
    private var minSeverity: KLogSeverity = KLogSeverity.None

    override fun setMinSeverity(severity: KLogSeverity) {
        minSeverity = severity
    }

    override fun log(severity: KLogSeverity, tag: String?, t: Throwable?, msg: () -> String) {
        if (severity.ordinal < minSeverity.ordinal) return
        val prefix = buildString {
            append("[ComposeWebView]")
            if (!tag.isNullOrBlank()) append("[$tag]")
            append(" ")
        }
        println(prefix + msg())
        if (t != null) println(t.toString())
    }
}

enum class KLogSeverity {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert,
    None,
}
